package com.ecillie.fbomanager.workforce.internal.persistence;

import com.ecillie.fbomanager.platform.api.AuditMetadata;
import com.ecillie.fbomanager.platform.api.PersistenceExceptionMapper;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.Role;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.Worker;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerFilter;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerShift;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerShiftStatus;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerStatus;
import com.ecillie.fbomanager.workforce.api.WorkforceRepository;
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
public class JdbcWorkforceRepository implements WorkforceRepository {

	private static final String WORKER_COLUMNS = "worker_id, role_name, first_name, last_name, phone, email, is_active, created_at, updated_at";
	private static final String SHIFT_COLUMNS = "shift_id, worker_id, scheduled_start_at, scheduled_end_at, actual_start_at, actual_end_at, status, notes, created_at, updated_at";
	private static final Map<String, String> WORKER_SORTS = Map.of("workerId", "worker_id", "firstName", "first_name",
			"lastName", "last_name", "roleName", "role_name", "updatedAt", "updated_at");
	private static final Map<String, String> SHIFT_SORTS = Map.of("shiftId", "shift_id", "scheduledStartAt",
			"scheduled_start_at", "scheduledEndAt", "scheduled_end_at", "status", "status");
	private static final Map<String, String> STATUS_SORTS = Map.of("workerId", "s.worker_id", "firstName",
			"s.first_name", "lastName", "s.last_name", "roleName", "s.role_name", "atWork", "s.is_at_work");
	private final JdbcClient jdbc;
	private final PersistenceExceptionMapper failures;

	public JdbcWorkforceRepository(JdbcClient jdbc, PersistenceExceptionMapper failures) {
		this.jdbc = jdbc;
		this.failures = failures;
	}

	@Override
	public Role saveRole(Role role) {
		return translated(() -> this.jdbc.sql("""
				INSERT INTO roles (name, description) VALUES (:name, :description)
				ON CONFLICT (name) DO UPDATE SET description = EXCLUDED.description
				RETURNING name, description, created_at, updated_at
				""").param("name", role.name()).param("description", role.description())
				.query((row, ignored) -> new Role(row.getString("name"), row.getString("description"), audit(row)))
				.single());
	}

	@Override
	public Worker saveWorker(Worker worker) {
		return translated(() -> {
			JdbcClient.StatementSpec statement;
			if (worker.workerId() == null) {
				statement = this.jdbc.sql("""
						INSERT INTO workers (role_name, first_name, last_name, phone, email, is_active)
						VALUES (:role, :first, :last, :phone, :email, :active) RETURNING %s
						""".formatted(WORKER_COLUMNS));
			} else {
				statement = this.jdbc.sql("""
						UPDATE workers SET role_name = :role, first_name = :first, last_name = :last, phone = :phone,
						    email = :email, is_active = :active WHERE worker_id = :id RETURNING %s
						""".formatted(WORKER_COLUMNS)).param("id", worker.workerId());
			}
			return statement.param("role", worker.roleName()).param("first", worker.firstName())
					.param("last", worker.lastName()).param("phone", worker.phone()).param("email", worker.email())
					.param("active", worker.active()).query(JdbcWorkforceRepository::worker).optional()
					.orElseThrow(() -> new IllegalArgumentException("worker does not exist"));
		});
	}

	@Override
	public WorkerShift saveShift(WorkerShift shift) {
		return translated(() -> {
			JdbcClient.StatementSpec statement;
			if (shift.shiftId() == null) {
				statement = this.jdbc.sql("""
						INSERT INTO worker_shifts (worker_id, scheduled_start_at, scheduled_end_at, actual_start_at,
						    actual_end_at, status, notes)
						VALUES (:worker, :scheduledStart, :scheduledEnd, :actualStart, :actualEnd,
						    CAST(:status AS worker_shift_status), :notes) RETURNING %s
						""".formatted(SHIFT_COLUMNS));
			} else {
				statement = this.jdbc
						.sql("""
								UPDATE worker_shifts SET worker_id = :worker, scheduled_start_at = :scheduledStart,
								    scheduled_end_at = :scheduledEnd, actual_start_at = :actualStart, actual_end_at = :actualEnd,
								    status = CAST(:status AS worker_shift_status), notes = :notes
								WHERE shift_id = :id RETURNING %s
								"""
								.formatted(SHIFT_COLUMNS))
						.param("id", shift.shiftId());
			}
			return statement.param("worker", shift.workerId())
					.param("scheduledStart", timestamp(shift.scheduledStartAt()))
					.param("scheduledEnd", timestamp(shift.scheduledEndAt()))
					.param("actualStart", timestamp(shift.actualStartAt()))
					.param("actualEnd", timestamp(shift.actualEndAt())).param("status", shift.status().name())
					.param("notes", shift.notes()).query(JdbcWorkforceRepository::shift).optional()
					.orElseThrow(() -> new IllegalArgumentException("shift does not exist"));
		});
	}

	@Override
	public Optional<WorkerShift> findShift(long shiftId) {
		return shiftQuery(shiftId, false);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<WorkerShift> lockShift(long shiftId) {
		return translated(() -> shiftQuery(shiftId, true));
	}

	private Optional<WorkerShift> shiftQuery(long shiftId, boolean lock) {
		return this.jdbc
				.sql("SELECT " + SHIFT_COLUMNS + " FROM worker_shifts WHERE shift_id = :id"
						+ (lock ? " FOR UPDATE" : ""))
				.param("id", shiftId).query(JdbcWorkforceRepository::shift).optional();
	}

	@Override
	public Optional<Worker> findWorker(long workerId) {
		return workerQuery(workerId, false);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Worker> lockWorker(long workerId) {
		return translated(() -> workerQuery(workerId, true));
	}

	private Optional<Worker> workerQuery(long workerId, boolean lock) {
		return this.jdbc
				.sql("SELECT " + WORKER_COLUMNS + " FROM workers WHERE worker_id = :id" + (lock ? " FOR UPDATE" : ""))
				.param("id", workerId).query(JdbcWorkforceRepository::worker).optional();
	}

	@Override
	public Optional<WorkerStatus> findCurrentStatus(long workerId) {
		return this.jdbc.sql("""
				SELECT worker_id, first_name, last_name, role_name, is_at_work, current_task_id, current_task_title
				FROM worker_current_status WHERE worker_id = :worker
				""").param("worker", workerId).query(JdbcWorkforceRepository::workerStatus).optional();
	}

	@Override
	public RepositoryPage<Worker> findWorkers(WorkerFilter filter, RepositoryPageRequest page) {
		String order = WORKER_SORTS.get(page.requireAllowedSort(WORKER_SORTS.keySet()));
		QueryParts query = filters(filter, "");
		long total = this.jdbc.sql("SELECT count(*) FROM workers" + query.where()).params(query.params())
				.query(Long.class).single();
		query.params().put("limit", page.limit());
		query.params().put("offset", page.offset());
		String sql = "SELECT " + WORKER_COLUMNS + " FROM workers" + query.where() + " ORDER BY " + order + " "
				+ page.direction() + ", worker_id ASC LIMIT :limit OFFSET :offset";
		return new RepositoryPage<>(
				this.jdbc.sql(sql).params(query.params()).query(JdbcWorkforceRepository::worker).list(), page.offset(),
				page.limit(), total);
	}

	@Override
	public RepositoryPage<WorkerShift> findShifts(long workerId, Instant from, Instant before,
			RepositoryPageRequest page) {
		if (from != null && before != null && !from.isBefore(before)) {
			throw new IllegalArgumentException("from must be before before");
		}
		String order = SHIFT_SORTS.get(page.requireAllowedSort(SHIFT_SORTS.keySet()));
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("worker", workerId);
		List<String> predicates = new ArrayList<>();
		predicates.add("worker_id = :worker");
		if (from != null) {
			predicates.add("scheduled_end_at > :from");
			params.put("from", timestamp(from));
		}
		if (before != null) {
			predicates.add("scheduled_start_at < :before");
			params.put("before", timestamp(before));
		}
		String where = " WHERE " + String.join(" AND ", predicates);
		long total = this.jdbc.sql("SELECT count(*) FROM worker_shifts" + where).params(params).query(Long.class)
				.single();
		params.put("limit", page.limit());
		params.put("offset", page.offset());
		String sql = "SELECT " + SHIFT_COLUMNS + " FROM worker_shifts" + where + " ORDER BY " + order + " "
				+ page.direction() + ", shift_id ASC LIMIT :limit OFFSET :offset";
		return new RepositoryPage<>(this.jdbc.sql(sql).params(params).query(JdbcWorkforceRepository::shift).list(),
				page.offset(), page.limit(), total);
	}

	@Override
	public RepositoryPage<WorkerStatus> findCurrentStatuses(WorkerFilter filter, RepositoryPageRequest page) {
		String order = STATUS_SORTS.get(page.requireAllowedSort(STATUS_SORTS.keySet()));
		QueryParts query = filters(filter, "w.");
		String from = " FROM worker_current_status s JOIN workers w ON w.worker_id = s.worker_id";
		long total = this.jdbc.sql("SELECT count(*)" + from + query.where()).params(query.params()).query(Long.class)
				.single();
		query.params().put("limit", page.limit());
		query.params().put("offset", page.offset());
		String sql = "SELECT s.worker_id, s.first_name, s.last_name, s.role_name, s.is_at_work, s.current_task_id, s.current_task_title"
				+ from + query.where() + " ORDER BY " + order + " " + page.direction()
				+ ", s.worker_id ASC LIMIT :limit OFFSET :offset";
		return new RepositoryPage<>(
				this.jdbc.sql(sql).params(query.params()).query(JdbcWorkforceRepository::workerStatus).list(),
				page.offset(), page.limit(), total);
	}

	private static QueryParts filters(WorkerFilter filter, String prefix) {
		Map<String, Object> params = new LinkedHashMap<>();
		List<String> predicates = new ArrayList<>();
		if (filter.active() != null) {
			predicates.add(prefix + "is_active = :active");
			params.put("active", filter.active());
		}
		if (filter.roleName() != null) {
			predicates.add(prefix + "role_name = :role");
			params.put("role", filter.roleName());
		}
		if (!filter.shiftStatuses().isEmpty()) {
			predicates.add("EXISTS (SELECT 1 FROM worker_shifts fs WHERE fs.worker_id = " + prefix
					+ "worker_id AND fs.status::text IN (:shiftStatuses))");
			params.put("shiftStatuses", filter.shiftStatuses().stream().map(Enum::name).toList());
		}
		if (filter.nameContains() != null) {
			predicates.add("(" + prefix + "first_name ILIKE :name OR " + prefix + "last_name ILIKE :name)");
			params.put("name", "%" + filter.nameContains() + "%");
		}
		return new QueryParts(predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates), params);
	}

	private <T> T translated(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (RuntimeException failure) {
			throw this.failures.map(failure);
		}
	}

	private static Worker worker(ResultSet row, int ignored) throws SQLException {
		return new Worker(row.getLong("worker_id"), row.getString("role_name"), row.getString("first_name"),
				row.getString("last_name"), row.getString("phone"), row.getString("email"), row.getBoolean("is_active"),
				audit(row));
	}

	private static WorkerStatus workerStatus(ResultSet row, int ignored) throws SQLException {
		return new WorkerStatus(row.getLong("worker_id"), row.getString("first_name"), row.getString("last_name"),
				row.getString("role_name"), row.getBoolean("is_at_work"), nullableLong(row, "current_task_id"),
				row.getString("current_task_title"));
	}

	private static WorkerShift shift(ResultSet row, int ignored) throws SQLException {
		return new WorkerShift(row.getLong("shift_id"), row.getLong("worker_id"), instant(row, "scheduled_start_at"),
				instant(row, "scheduled_end_at"), instant(row, "actual_start_at"), instant(row, "actual_end_at"),
				WorkerShiftStatus.valueOf(row.getString("status")), row.getString("notes"), audit(row));
	}

	private static Long nullableLong(ResultSet row, String column) throws SQLException {
		long value = row.getLong(column);
		return row.wasNull() ? null : value;
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

	private record QueryParts(String where, Map<String, Object> params) {
	}
}
