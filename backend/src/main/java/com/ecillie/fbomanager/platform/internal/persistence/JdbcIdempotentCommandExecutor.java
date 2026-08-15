package com.ecillie.fbomanager.platform.internal.persistence;

import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.DomainConflictException;
import com.ecillie.fbomanager.platform.api.DomainUnexpectedException;
import com.ecillie.fbomanager.platform.api.IdempotencyKey;
import com.ecillie.fbomanager.platform.api.IdempotentCommandExecutor;
import com.ecillie.fbomanager.platform.api.IdempotentResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

@Component
public class JdbcIdempotentCommandExecutor implements IdempotentCommandExecutor {

	private static final Duration MINIMUM_RETENTION = Duration.ofHours(24);
	private static final String API_VERSION = "v1";
	private final JdbcClient jdbc;
	private final ObjectMapper mapper;
	private final Clock clock;

	public JdbcIdempotentCommandExecutor(JdbcClient jdbc, ObjectMapper mapper, Clock clock) {
		this.jdbc = jdbc;
		this.mapper = mapper.rebuild().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
				.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
		this.clock = clock;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public <T> IdempotentResult<T> execute(String operationId, IdempotencyKey key, Object command, ActorContext actor,
			int responseStatus, Duration retention, Class<T> resultType, Supplier<T> action) {
		Duration effectiveRetention = retention.compareTo(MINIMUM_RETENTION) < 0 ? MINIMUM_RETENTION : retention;
		return executeInternal(operationId, key, command, actor, responseStatus, resultType, ignored -> null,
				effectiveRetention, action);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public <T> IdempotentResult<T> executeInventory(String operationId, IdempotencyKey key, Object command,
			ActorContext actor, int responseStatus, Class<T> resultType, Function<T, Long> evidenceTransaction,
			Supplier<T> action) {
		return executeInternal(operationId, key, command, actor, responseStatus, resultType, evidenceTransaction, null,
				action);
	}

	private <T> IdempotentResult<T> executeInternal(String operationId, IdempotencyKey key, Object command,
			ActorContext actor, int responseStatus, Class<T> resultType, Function<T, Long> evidenceTransaction,
			Duration retention, Supplier<T> action) {
		String fingerprint = fingerprint(command, actor);
		Instant now = Instant.now(this.clock).truncatedTo(ChronoUnit.MICROS);
		Instant placeholderExpiry = now.plus(MINIMUM_RETENTION);
		int inserted = this.jdbc.sql("""
				INSERT INTO idempotency_records (api_version, operation_id, idempotency_key, command_fingerprint,
				    response_status, response_body, completed_at, expires_at)
				VALUES (:version, :operation, :key, :fingerprint, 500, '{}'::jsonb, :completed, :expires)
				ON CONFLICT (api_version, operation_id, idempotency_key) DO NOTHING
				""").param("version", API_VERSION).param("operation", operationId).param("key", key.value())
				.param("fingerprint", fingerprint).param("completed", timestamp(now))
				.param("expires", timestamp(placeholderExpiry)).update();
		if (inserted == 0) {
			StoredResult stored = load(operationId, key);
			if (!stored.fingerprint().equals(fingerprint)) {
				throw new DomainConflictException("IDEMPOTENCY_KEY_REUSED",
						"The idempotency key was already used for a different command.",
						Map.of("operationId", operationId));
			}
			return new IdempotentResult<>(read(stored.body(), resultType), stored.status(), true);
		}

		T result = action.get();
		Long evidence = evidenceTransaction.apply(result);
		if (retention == null && evidence == null) {
			throw new DomainUnexpectedException("IDEMPOTENCY_FAILURE",
					"The inventory command did not produce ledger evidence.", null);
		}
		Instant expires = evidence == null ? now.plus(retention) : null;
		String body = write(result);
		this.jdbc.sql("""
				UPDATE idempotency_records SET response_status = :status, response_body = CAST(:body AS jsonb),
				    completed_at = :completed, expires_at = :expires, fuel_transaction_id = :evidence
				WHERE api_version = :version AND operation_id = :operation AND idempotency_key = :key
				""").param("status", responseStatus).param("body", body).param("completed", timestamp(now))
				.param("expires", timestamp(expires)).param("evidence", evidence).param("version", API_VERSION)
				.param("operation", operationId).param("key", key.value()).update();
		return new IdempotentResult<>(result, responseStatus, false);
	}

	private StoredResult load(String operationId, IdempotencyKey key) {
		return this.jdbc.sql("""
				SELECT command_fingerprint, response_status, response_body::text AS response_body
				FROM idempotency_records
				WHERE api_version = :version AND operation_id = :operation AND idempotency_key = :key
				""").param("version", API_VERSION).param("operation", operationId).param("key", key.value())
				.query((row, ignored) -> new StoredResult(row.getString("command_fingerprint"),
						row.getInt("response_status"), row.getString("response_body")))
				.single();
	}

	private String fingerprint(Object command, ActorContext actor) {
		List<String> capabilities = actor.capabilities().stream().map(Enum::name).sorted().toList();
		String canonical = write(Map.of("actor", actor.subject(), "capabilities", capabilities, "command", command));
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException failure) {
			throw new DomainUnexpectedException("IDEMPOTENCY_FAILURE", "The command could not be fingerprinted.",
					failure);
		}
	}

	private String write(Object value) {
		try {
			return this.mapper.writeValueAsString(value);
		} catch (JacksonException failure) {
			throw new DomainUnexpectedException("IDEMPOTENCY_FAILURE", "The command outcome could not be recorded.",
					failure);
		}
	}

	private <T> T read(String value, Class<T> resultType) {
		try {
			return this.mapper.readValue(value, resultType);
		} catch (JacksonException failure) {
			throw new DomainUnexpectedException("IDEMPOTENCY_FAILURE",
					"The recorded command outcome could not be read.", failure);
		}
	}

	private static OffsetDateTime timestamp(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}

	private record StoredResult(String fingerprint, int status, String body) {
	}
}
