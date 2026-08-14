package com.ecillie.fbomanager.services.internal.persistence;

import com.ecillie.fbomanager.platform.api.AuditMetadata;
import com.ecillie.fbomanager.platform.api.FixedPrecisionQuantity;
import com.ecillie.fbomanager.platform.api.NaturalKey;
import com.ecillie.fbomanager.platform.api.PersistenceExceptionMapper;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceRequest;
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceRequestFilter;
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceRequestStatus;
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceType;
import com.ecillie.fbomanager.services.api.ServiceRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcServiceRepository implements ServiceRepository {

	private static final String REQUEST_COLUMNS = "service_request_id, aircraft_visit_id, service_type_code, fuel_type_code, status, requested_quantity, quantity_unit, notes, completed_at, created_at, updated_at";
	private static final Map<String, String> SORTS = Map.of("serviceRequestId", "service_request_id", "aircraftVisitId",
			"aircraft_visit_id", "status", "status", "serviceTypeCode", "service_type_code", "createdAt", "created_at",
			"completedAt", "completed_at");
	private final JdbcClient jdbc;
	private final PersistenceExceptionMapper failures;

	public JdbcServiceRepository(JdbcClient jdbc, PersistenceExceptionMapper failures) {
		this.jdbc = jdbc;
		this.failures = failures;
	}

	@Override
	public ServiceType saveType(ServiceType type) {
		return translated(() -> this.jdbc.sql("""
				INSERT INTO service_types (code, name, is_fuel_service, default_unit, is_active)
				VALUES (:code, :name, :fuel, :unit, :active)
				ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, is_fuel_service = EXCLUDED.is_fuel_service,
				    default_unit = EXCLUDED.default_unit, is_active = EXCLUDED.is_active
				RETURNING code, name, is_fuel_service, default_unit, is_active, created_at, updated_at
				""").param("code", type.code()).param("name", type.name()).param("fuel", type.fuelService())
				.param("unit", type.defaultUnit()).param("active", type.active())
				.query((row, ignored) -> new ServiceType(row.getString("code"), row.getString("name"),
						row.getBoolean("is_fuel_service"), row.getString("default_unit"), row.getBoolean("is_active"),
						audit(row)))
				.single());
	}

	@Override
	public Optional<ServiceType> findType(String code) {
		return this.jdbc.sql("""
				SELECT code, name, is_fuel_service, default_unit, is_active, created_at, updated_at
				FROM service_types WHERE code = :code
				""").param("code", NaturalKey.code(code))
				.query((row, ignored) -> new ServiceType(row.getString("code"), row.getString("name"),
						row.getBoolean("is_fuel_service"), row.getString("default_unit"), row.getBoolean("is_active"),
						audit(row)))
				.optional();
	}

	@Override
	public ServiceRequest saveRequest(ServiceRequest request) {
		return translated(() -> {
			JdbcClient.StatementSpec statement;
			if (request.serviceRequestId() == null) {
				statement = this.jdbc.sql("""
						INSERT INTO service_requests (aircraft_visit_id, service_type_code, fuel_type_code, status,
						    requested_quantity, quantity_unit, notes, completed_at)
						VALUES (:visit, :type, :fuel, CAST(:status AS service_request_status), :quantity, :unit, :notes,
						    :completed) RETURNING %s
						""".formatted(REQUEST_COLUMNS));
			} else {
				statement = this.jdbc.sql("""
						UPDATE service_requests SET aircraft_visit_id = :visit, service_type_code = :type,
						    fuel_type_code = :fuel, status = CAST(:status AS service_request_status),
						    requested_quantity = :quantity, quantity_unit = :unit, notes = :notes,
						    completed_at = :completed WHERE service_request_id = :id RETURNING %s
						""".formatted(REQUEST_COLUMNS)).param("id", request.serviceRequestId());
			}
			return statement.param("visit", request.aircraftVisitId()).param("type", request.serviceTypeCode())
					.param("fuel", request.fuelTypeCode()).param("status", request.status().name())
					.param("quantity", decimal(request.requestedQuantity())).param("unit", request.quantityUnit())
					.param("notes", request.notes()).param("completed", timestamp(request.completedAt()))
					.query(JdbcServiceRepository::request).optional()
					.orElseThrow(() -> new IllegalArgumentException("service request does not exist"));
		});
	}

	@Override
	public Optional<ServiceRequest> findRequest(long serviceRequestId) {
		return requestQuery(serviceRequestId, false);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<ServiceRequest> lockRequest(long serviceRequestId) {
		return translated(() -> requestQuery(serviceRequestId, true));
	}

	private Optional<ServiceRequest> requestQuery(long id, boolean lock) {
		return this.jdbc.sql("SELECT " + REQUEST_COLUMNS + " FROM service_requests WHERE service_request_id = :id"
				+ (lock ? " FOR UPDATE" : "")).param("id", id).query(JdbcServiceRepository::request).optional();
	}

	@Override
	public RepositoryPage<ServiceRequest> findRequests(ServiceRequestFilter filter, RepositoryPageRequest page) {
		String order = SORTS.get(page.requireAllowedSort(SORTS.keySet()));
		Map<String, Object> params = new LinkedHashMap<>();
		List<String> predicates = new ArrayList<>();
		if (filter.aircraftVisitId() != null) {
			predicates.add("aircraft_visit_id = :visit");
			params.put("visit", filter.aircraftVisitId());
		}
		if (!filter.statuses().isEmpty()) {
			predicates.add("status::text IN (:statuses)");
			params.put("statuses", filter.statuses().stream().map(Enum::name).toList());
		}
		if (filter.serviceTypeCode() != null) {
			predicates.add("service_type_code = :type");
			params.put("type", filter.serviceTypeCode());
		}
		String where = predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
		long total = this.jdbc.sql("SELECT count(*) FROM service_requests" + where).params(params).query(Long.class)
				.single();
		params.put("limit", page.limit());
		params.put("offset", page.offset());
		String sql = "SELECT " + REQUEST_COLUMNS + " FROM service_requests" + where + " ORDER BY " + order + " "
				+ page.direction() + ", service_request_id ASC LIMIT :limit OFFSET :offset";
		return new RepositoryPage<>(this.jdbc.sql(sql).params(params).query(JdbcServiceRepository::request).list(),
				page.offset(), page.limit(), total);
	}

	private <T> T translated(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (RuntimeException failure) {
			throw this.failures.map(failure);
		}
	}

	private static ServiceRequest request(ResultSet row, int ignored) throws SQLException {
		return new ServiceRequest(row.getLong("service_request_id"), row.getLong("aircraft_visit_id"),
				row.getString("service_type_code"), row.getString("fuel_type_code"),
				ServiceRequestStatus.valueOf(row.getString("status")), quantity(row, "requested_quantity"),
				row.getString("quantity_unit"), row.getString("notes"), instant(row, "completed_at"), audit(row));
	}

	private static java.math.BigDecimal decimal(FixedPrecisionQuantity quantity) {
		return quantity == null ? null : quantity.toBigDecimal();
	}

	private static FixedPrecisionQuantity quantity(ResultSet row, String column) throws SQLException {
		java.math.BigDecimal value = row.getBigDecimal(column);
		return value == null ? null : FixedPrecisionQuantity.from(value);
	}

	private static Instant instant(ResultSet row, String column) throws SQLException {
		OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	private static OffsetDateTime timestamp(Instant value) {
		return value == null ? null : value.atOffset(ZoneOffset.UTC);
	}

	private static AuditMetadata audit(ResultSet row) throws SQLException {
		return new AuditMetadata(instant(row, "created_at"), instant(row, "updated_at"));
	}
}
