package com.ecillie.fbomanager.tasks.internal.persistence;

import com.ecillie.fbomanager.platform.api.AuditMetadata;
import com.ecillie.fbomanager.platform.api.PersistenceExceptionMapper;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import com.ecillie.fbomanager.tasks.api.TaskModels.AirportTask;
import com.ecillie.fbomanager.tasks.api.TaskModels.TaskFilter;
import com.ecillie.fbomanager.tasks.api.TaskModels.TaskStatus;
import com.ecillie.fbomanager.tasks.api.TaskRepository;
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
public class JdbcTaskRepository implements TaskRepository {

	private static final String COLUMNS = "task_id, aircraft_visit_id, service_request_id, service_vehicle_identifier, assigned_worker_id, title, description, status, due_at, started_at, completed_at, created_at, updated_at";
	private static final Map<String, String> SORTS = Map.of("taskId", "task_id", "aircraftVisitId", "aircraft_visit_id",
			"status", "status", "dueAt", "due_at", "createdAt", "created_at", "updatedAt", "updated_at");
	private final JdbcClient jdbc;
	private final PersistenceExceptionMapper failures;

	public JdbcTaskRepository(JdbcClient jdbc, PersistenceExceptionMapper failures) {
		this.jdbc = jdbc;
		this.failures = failures;
	}

	@Override
	public AirportTask save(AirportTask task) {
		return translated(() -> {
			JdbcClient.StatementSpec statement;
			if (task.taskId() == null) {
				statement = this.jdbc.sql("""
						INSERT INTO tasks (aircraft_visit_id, service_request_id, service_vehicle_identifier,
						    assigned_worker_id, title, description, status, due_at, started_at, completed_at)
						VALUES (:visit, :request, :vehicle, :worker, :title, :description, CAST(:status AS task_status),
						    :due, :started, :completed) RETURNING %s
						""".formatted(COLUMNS));
			} else {
				statement = this.jdbc.sql("""
						UPDATE tasks SET aircraft_visit_id = :visit, service_request_id = :request,
						    service_vehicle_identifier = :vehicle, assigned_worker_id = :worker, title = :title,
						    description = :description, status = CAST(:status AS task_status), due_at = :due,
						    started_at = :started, completed_at = :completed WHERE task_id = :id RETURNING %s
						""".formatted(COLUMNS)).param("id", task.taskId());
			}
			return statement.param("visit", task.aircraftVisitId()).param("request", task.serviceRequestId())
					.param("vehicle", task.serviceVehicleIdentifier()).param("worker", task.assignedWorkerId())
					.param("title", task.title()).param("description", task.description())
					.param("status", task.status().name()).param("due", timestamp(task.dueAt()))
					.param("started", timestamp(task.startedAt())).param("completed", timestamp(task.completedAt()))
					.query(JdbcTaskRepository::task).optional()
					.orElseThrow(() -> new IllegalArgumentException("task does not exist"));
		});
	}

	@Override
	public Optional<AirportTask> find(long taskId) {
		return taskQuery(taskId, false);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<AirportTask> lockForAssignment(long taskId) {
		return translated(() -> taskQuery(taskId, true));
	}

	private Optional<AirportTask> taskQuery(long taskId, boolean lock) {
		return this.jdbc.sql("SELECT " + COLUMNS + " FROM tasks WHERE task_id = :id" + (lock ? " FOR UPDATE" : ""))
				.param("id", taskId).query(JdbcTaskRepository::task).optional();
	}

	@Override
	public RepositoryPage<AirportTask> findTasks(TaskFilter filter, RepositoryPageRequest page) {
		String order = SORTS.get(page.requireAllowedSort(SORTS.keySet()));
		Map<String, Object> params = new LinkedHashMap<>();
		List<String> predicates = new ArrayList<>();
		if (filter.aircraftVisitId() != null) {
			predicates.add("aircraft_visit_id = :visit");
			params.put("visit", filter.aircraftVisitId());
		}
		if (filter.serviceRequestId() != null) {
			predicates.add("service_request_id = :request");
			params.put("request", filter.serviceRequestId());
		}
		if (filter.assignedWorkerId() != null) {
			predicates.add("assigned_worker_id = :worker");
			params.put("worker", filter.assignedWorkerId());
		}
		if (filter.serviceVehicleIdentifier() != null) {
			predicates.add("service_vehicle_identifier = :vehicle");
			params.put("vehicle", filter.serviceVehicleIdentifier());
		}
		if (!filter.statuses().isEmpty()) {
			predicates.add("status::text IN (:statuses)");
			params.put("statuses", filter.statuses().stream().map(Enum::name).toList());
		}
		if (filter.dueFrom() != null) {
			predicates.add("due_at >= :dueFrom");
			params.put("dueFrom", timestamp(filter.dueFrom()));
		}
		if (filter.dueBefore() != null) {
			predicates.add("due_at < :dueBefore");
			params.put("dueBefore", timestamp(filter.dueBefore()));
		}
		String where = predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
		long total = this.jdbc.sql("SELECT count(*) FROM tasks" + where).params(params).query(Long.class).single();
		params.put("limit", page.limit());
		params.put("offset", page.offset());
		String sql = "SELECT " + COLUMNS + " FROM tasks" + where + " ORDER BY " + order + " " + page.direction()
				+ ", task_id ASC LIMIT :limit OFFSET :offset";
		return new RepositoryPage<>(this.jdbc.sql(sql).params(params).query(JdbcTaskRepository::task).list(),
				page.offset(), page.limit(), total);
	}

	private <T> T translated(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (RuntimeException failure) {
			throw this.failures.map(failure);
		}
	}

	private static AirportTask task(ResultSet row, int ignored) throws SQLException {
		return new AirportTask(row.getLong("task_id"), nullableLong(row, "aircraft_visit_id"),
				nullableLong(row, "service_request_id"), row.getString("service_vehicle_identifier"),
				nullableLong(row, "assigned_worker_id"), row.getString("title"), row.getString("description"),
				TaskStatus.valueOf(row.getString("status")), instant(row, "due_at"), instant(row, "started_at"),
				instant(row, "completed_at"), audit(row));
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
}
