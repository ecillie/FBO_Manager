package com.ecillie.fbomanager.visits.internal.persistence;

import com.ecillie.fbomanager.platform.api.AuditMetadata;
import com.ecillie.fbomanager.platform.api.FixedPrecisionQuantity;
import com.ecillie.fbomanager.platform.api.PersistenceExceptionMapper;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import com.ecillie.fbomanager.visits.api.VisitModels.AircraftVisit;
import com.ecillie.fbomanager.visits.api.VisitModels.OperationalVisitDetail;
import com.ecillie.fbomanager.visits.api.VisitModels.ServiceSummary;
import com.ecillie.fbomanager.visits.api.VisitModels.TaskSummary;
import com.ecillie.fbomanager.visits.api.VisitModels.VisitDetail;
import com.ecillie.fbomanager.visits.api.VisitModels.VisitFilter;
import com.ecillie.fbomanager.visits.api.VisitModels.VisitStatus;
import com.ecillie.fbomanager.visits.api.VisitRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
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
public class JdbcVisitRepository implements VisitRepository {

	private static final String COLUMNS = "visit_id, tail_number, parking_spot_code, status, estimated_arrival_at, actual_arrival_at, estimated_departure_at, actual_departure_at, notes, created_at, updated_at";
	private static final String DETAIL_SELECT = "v.visit_id, v.tail_number, v.parking_spot_code, v.status, v.estimated_arrival_at, v.actual_arrival_at, v.estimated_departure_at, v.actual_departure_at, v.notes, v.created_at, v.updated_at, a.manufacturer_name, a.model_name, m.aircraft_category_code, a.aircraft_operation_type_code, a.fuel_type_code, p.parking_area_code, p.name AS parking_spot_name";
	private static final String DETAIL_FROM = " FROM aircraft_visits v JOIN aircraft a ON a.tail_number = v.tail_number JOIN aircraft_models m ON m.manufacturer_name = a.manufacturer_name AND m.model_name = a.model_name LEFT JOIN parking_spots p ON p.spot_code = v.parking_spot_code";
	private static final Map<String, String> SORTS = Map.of("visitId", "v.visit_id", "tailNumber", "v.tail_number",
			"status", "v.status", "estimatedArrivalAt", "v.estimated_arrival_at", "estimatedDepartureAt",
			"v.estimated_departure_at", "updatedAt", "v.updated_at");
	private final JdbcClient jdbc;
	private final PersistenceExceptionMapper failures;

	public JdbcVisitRepository(JdbcClient jdbc, PersistenceExceptionMapper failures) {
		this.jdbc = jdbc;
		this.failures = failures;
	}

	@Override
	public AircraftVisit save(AircraftVisit visit) {
		return translated(() -> {
			JdbcClient.StatementSpec statement;
			if (visit.visitId() == null) {
				statement = this.jdbc.sql("""
						INSERT INTO aircraft_visits (tail_number, parking_spot_code, status, estimated_arrival_at,
						    actual_arrival_at, estimated_departure_at, actual_departure_at, notes)
						VALUES (:tail, :spot, CAST(:status AS visit_status), :estimatedArrival, :actualArrival,
						    :estimatedDeparture, :actualDeparture, :notes) RETURNING %s
						""".formatted(COLUMNS));
			} else {
				statement = this.jdbc.sql("""
						UPDATE aircraft_visits SET tail_number = :tail, parking_spot_code = :spot,
						    status = CAST(:status AS visit_status), estimated_arrival_at = :estimatedArrival,
						    actual_arrival_at = :actualArrival, estimated_departure_at = :estimatedDeparture,
						    actual_departure_at = :actualDeparture, notes = :notes
						WHERE visit_id = :id RETURNING %s
						""".formatted(COLUMNS)).param("id", visit.visitId());
			}
			return statement.param("tail", visit.tailNumber()).param("spot", visit.parkingSpotCode())
					.param("status", visit.status().name())
					.param("estimatedArrival", timestamp(visit.estimatedArrivalAt()))
					.param("actualArrival", timestamp(visit.actualArrivalAt()))
					.param("estimatedDeparture", timestamp(visit.estimatedDepartureAt()))
					.param("actualDeparture", timestamp(visit.actualDepartureAt())).param("notes", visit.notes())
					.query(JdbcVisitRepository::visit).optional()
					.orElseThrow(() -> new IllegalArgumentException("visit does not exist"));
		});
	}

	@Override
	public Optional<AircraftVisit> find(long visitId) {
		return visitQuery(visitId, false);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<AircraftVisit> lock(long visitId) {
		return translated(() -> visitQuery(visitId, true));
	}

	private Optional<AircraftVisit> visitQuery(long visitId, boolean lock) {
		return this.jdbc
				.sql("SELECT " + COLUMNS + " FROM aircraft_visits WHERE visit_id = :id" + (lock ? " FOR UPDATE" : ""))
				.param("id", visitId).query(JdbcVisitRepository::visit).optional();
	}

	@Override
	public Optional<VisitDetail> findDetail(long visitId) {
		return this.jdbc.sql("SELECT " + DETAIL_SELECT + DETAIL_FROM + " WHERE v.visit_id = :id").param("id", visitId)
				.query(JdbcVisitRepository::detail).optional();
	}

	@Override
	public Optional<OperationalVisitDetail> findOperationalDetail(long visitId) {
		String sql = "SELECT " + DETAIL_SELECT
				+ ", sr.service_request_id, sr.service_type_code, sr.status::text AS service_status,"
				+ " sr.fuel_type_code AS service_fuel_type_code, sr.requested_quantity, sr.quantity_unit,"
				+ " t.task_id, t.title AS task_title, t.status::text AS task_status, t.assigned_worker_id,"
				+ " t.service_vehicle_identifier" + DETAIL_FROM
				+ " LEFT JOIN service_requests sr ON sr.aircraft_visit_id = v.visit_id"
				+ " LEFT JOIN tasks t ON t.aircraft_visit_id = v.visit_id WHERE v.visit_id = :id"
				+ " ORDER BY sr.service_request_id, t.task_id";
		List<OperationalRow> rows = this.jdbc.sql(sql).param("id", visitId).query(JdbcVisitRepository::operationalRow)
				.list();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Map<Long, ServiceSummary> services = new LinkedHashMap<>();
		Map<Long, TaskSummary> tasks = new LinkedHashMap<>();
		for (OperationalRow row : rows) {
			if (row.service() != null) {
				services.putIfAbsent(row.service().serviceRequestId(), row.service());
			}
			if (row.task() != null) {
				tasks.putIfAbsent(row.task().taskId(), row.task());
			}
		}
		return Optional.of(new OperationalVisitDetail(rows.getFirst().visit(), List.copyOf(services.values()),
				List.copyOf(tasks.values())));
	}

	@Override
	public RepositoryPage<VisitDetail> findOperationalDetails(VisitFilter filter, RepositoryPageRequest page) {
		String order = SORTS.get(page.requireAllowedSort(SORTS.keySet()));
		Map<String, Object> params = new LinkedHashMap<>();
		List<String> predicates = new ArrayList<>();
		if (!filter.statuses().isEmpty()) {
			predicates.add("v.status::text IN (:statuses)");
			params.put("statuses", filter.statuses().stream().map(Enum::name).toList());
		}
		if (filter.tailNumber() != null) {
			predicates.add("v.tail_number = :tail");
			params.put("tail", filter.tailNumber());
		}
		if (filter.parkingSpotCode() != null) {
			predicates.add("v.parking_spot_code = :spot");
			params.put("spot", filter.parkingSpotCode());
		}
		if (filter.arrivalFrom() != null) {
			predicates.add("COALESCE(v.actual_arrival_at, v.estimated_arrival_at) >= :arrivalFrom");
			params.put("arrivalFrom", timestamp(filter.arrivalFrom()));
		}
		if (filter.arrivalBefore() != null) {
			predicates.add("COALESCE(v.actual_arrival_at, v.estimated_arrival_at) < :arrivalBefore");
			params.put("arrivalBefore", timestamp(filter.arrivalBefore()));
		}
		String where = predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
		long total = this.jdbc.sql("SELECT count(*)" + DETAIL_FROM + where).params(params).query(Long.class).single();
		params.put("limit", page.limit());
		params.put("offset", page.offset());
		String sql = "SELECT " + DETAIL_SELECT + DETAIL_FROM + where + " ORDER BY " + order + " " + page.direction()
				+ ", v.visit_id ASC LIMIT :limit OFFSET :offset";
		return new RepositoryPage<>(this.jdbc.sql(sql).params(params).query(JdbcVisitRepository::detail).list(),
				page.offset(), page.limit(), total);
	}

	private <T> T translated(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (RuntimeException failure) {
			throw this.failures.map(failure);
		}
	}

	private static VisitDetail detail(ResultSet row, int ignored) throws SQLException {
		return new VisitDetail(visit(row, ignored), row.getString("manufacturer_name"), row.getString("model_name"),
				row.getString("aircraft_category_code"), row.getString("aircraft_operation_type_code"),
				row.getString("fuel_type_code"), row.getString("parking_area_code"),
				row.getString("parking_spot_name"));
	}

	private static OperationalRow operationalRow(ResultSet row, int ignored) throws SQLException {
		Long serviceId = nullableLong(row, "service_request_id");
		ServiceSummary service = serviceId == null
				? null
				: new ServiceSummary(serviceId, row.getString("service_type_code"), row.getString("service_status"),
						row.getString("service_fuel_type_code"), quantity(row, "requested_quantity"),
						row.getString("quantity_unit"));
		Long taskId = nullableLong(row, "task_id");
		TaskSummary task = taskId == null
				? null
				: new TaskSummary(taskId, row.getString("task_title"), row.getString("task_status"),
						nullableLong(row, "assigned_worker_id"), row.getString("service_vehicle_identifier"));
		return new OperationalRow(detail(row, ignored), service, task);
	}

	private static AircraftVisit visit(ResultSet row, int ignored) throws SQLException {
		return new AircraftVisit(row.getLong("visit_id"), row.getString("tail_number"),
				row.getString("parking_spot_code"), VisitStatus.valueOf(row.getString("status")),
				instant(row, "estimated_arrival_at"), instant(row, "actual_arrival_at"),
				instant(row, "estimated_departure_at"), instant(row, "actual_departure_at"), row.getString("notes"),
				audit(row));
	}

	private static java.time.Instant instant(ResultSet row, String column) throws SQLException {
		OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	private static Long nullableLong(ResultSet row, String column) throws SQLException {
		long value = row.getLong(column);
		return row.wasNull() ? null : value;
	}

	private static FixedPrecisionQuantity quantity(ResultSet row, String column) throws SQLException {
		java.math.BigDecimal value = row.getBigDecimal(column);
		return value == null ? null : FixedPrecisionQuantity.from(value);
	}

	private static OffsetDateTime timestamp(java.time.Instant value) {
		return value == null ? null : value.atOffset(ZoneOffset.UTC);
	}

	private static AuditMetadata audit(ResultSet row) throws SQLException {
		return new AuditMetadata(instant(row, "created_at"), instant(row, "updated_at"));
	}

	private record OperationalRow(VisitDetail visit, ServiceSummary service, TaskSummary task) {
	}
}
