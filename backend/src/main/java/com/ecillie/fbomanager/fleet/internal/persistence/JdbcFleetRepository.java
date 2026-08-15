package com.ecillie.fbomanager.fleet.internal.persistence;

import com.ecillie.fbomanager.fleet.api.FleetModels.FuelTruck;
import com.ecillie.fbomanager.fleet.api.FleetModels.ServiceVehicle;
import com.ecillie.fbomanager.fleet.api.FleetModels.ServiceVehicleType;
import com.ecillie.fbomanager.fleet.api.FleetModels.VehicleCurrentState;
import com.ecillie.fbomanager.fleet.api.FleetModels.VehicleFilter;
import com.ecillie.fbomanager.fleet.api.FleetModels.VehicleStatus;
import com.ecillie.fbomanager.fleet.api.FleetRepository;
import com.ecillie.fbomanager.platform.api.AuditMetadata;
import com.ecillie.fbomanager.platform.api.FixedPrecisionQuantity;
import com.ecillie.fbomanager.platform.api.NaturalKey;
import com.ecillie.fbomanager.platform.api.OperationalStatus;
import com.ecillie.fbomanager.platform.api.PersistenceExceptionMapper;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
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
public class JdbcFleetRepository implements FleetRepository {

	private static final String VEHICLE_COLUMNS = "identifier, service_vehicle_type_code, operational_status, notes, is_active, created_at, updated_at";
	private static final Map<String, String> VEHICLE_SORTS = Map.of("identifier", "identifier", "vehicleTypeCode",
			"service_vehicle_type_code", "operationalStatus", "operational_status", "updatedAt", "updated_at");
	private static final Map<String, String> STATUS_SORTS = Map.of("identifier", "s.identifier", "vehicleTypeCode",
			"s.service_vehicle_type_code", "currentStatus", "s.current_status", "currentTailNumber",
			"s.current_tail_number");
	private final JdbcClient jdbc;
	private final PersistenceExceptionMapper failures;

	public JdbcFleetRepository(JdbcClient jdbc, PersistenceExceptionMapper failures) {
		this.jdbc = jdbc;
		this.failures = failures;
	}

	@Override
	public ServiceVehicleType saveType(ServiceVehicleType type) {
		return translated(() -> this.jdbc.sql("""
				INSERT INTO service_vehicle_types (code, name, is_fuel_truck) VALUES (:code, :name, :fuel)
				ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, is_fuel_truck = EXCLUDED.is_fuel_truck
				RETURNING code, name, is_fuel_truck, created_at, updated_at
				""").param("code", type.code()).param("name", type.name()).param("fuel", type.fuelTruck())
				.query((row, ignored) -> new ServiceVehicleType(row.getString("code"), row.getString("name"),
						row.getBoolean("is_fuel_truck"), audit(row)))
				.single());
	}

	@Override
	public Optional<ServiceVehicleType> findType(String code) {
		return this.jdbc.sql("""
				SELECT code, name, is_fuel_truck, created_at, updated_at
				FROM service_vehicle_types WHERE code = :code
				""").param("code", NaturalKey.code(code))
				.query((row, ignored) -> new ServiceVehicleType(row.getString("code"), row.getString("name"),
						row.getBoolean("is_fuel_truck"), audit(row)))
				.optional();
	}

	@Override
	public ServiceVehicle saveVehicle(ServiceVehicle vehicle) {
		return translated(() -> this.jdbc
				.sql("""
						INSERT INTO service_vehicles (identifier, service_vehicle_type_code, operational_status, notes, is_active)
						VALUES (:identifier, :type, CAST(:status AS operational_status), :notes, :active)
						ON CONFLICT (identifier) DO UPDATE SET service_vehicle_type_code = EXCLUDED.service_vehicle_type_code,
						    operational_status = EXCLUDED.operational_status, notes = EXCLUDED.notes,
						    is_active = EXCLUDED.is_active RETURNING %s
						"""
						.formatted(VEHICLE_COLUMNS))
				.param("identifier", vehicle.identifier()).param("type", vehicle.serviceVehicleTypeCode())
				.param("status", vehicle.status().name()).param("notes", vehicle.notes())
				.param("active", vehicle.active()).query(JdbcFleetRepository::vehicle).single());
	}

	@Override
	public FuelTruck saveFuelTruck(FuelTruck truck) {
		return translated(() -> this.jdbc.sql("""
				INSERT INTO fuel_trucks (service_vehicle_identifier, fuel_type_code, capacity, quantity_unit)
				VALUES (:identifier, :fuel, :capacity, :unit)
				ON CONFLICT (service_vehicle_identifier) DO UPDATE SET fuel_type_code = EXCLUDED.fuel_type_code,
				    capacity = EXCLUDED.capacity, quantity_unit = EXCLUDED.quantity_unit
				RETURNING service_vehicle_identifier, fuel_type_code, capacity, quantity_unit, created_at, updated_at
				""").param("identifier", truck.serviceVehicleIdentifier()).param("fuel", truck.fuelTypeCode())
				.param("capacity", truck.capacity().toBigDecimal()).param("unit", truck.quantityUnit())
				.query((row, ignored) -> new FuelTruck(row.getString("service_vehicle_identifier"),
						row.getString("fuel_type_code"), FixedPrecisionQuantity.from(row.getBigDecimal("capacity")),
						row.getString("quantity_unit"), audit(row)))
				.single());
	}

	@Override
	public Optional<FuelTruck> findFuelTruck(String vehicleIdentifier) {
		return this.jdbc.sql("""
				SELECT service_vehicle_identifier, fuel_type_code, capacity, quantity_unit, created_at, updated_at
				FROM fuel_trucks WHERE service_vehicle_identifier = :identifier
				""").param("identifier", NaturalKey.identifier(vehicleIdentifier))
				.query((row, ignored) -> new FuelTruck(row.getString("service_vehicle_identifier"),
						row.getString("fuel_type_code"), FixedPrecisionQuantity.from(row.getBigDecimal("capacity")),
						row.getString("quantity_unit"), audit(row)))
				.optional();
	}

	@Override
	public Optional<ServiceVehicle> findVehicle(String identifier) {
		return vehicleQuery(identifier, false);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<ServiceVehicle> lockVehicle(String identifier) {
		return translated(() -> vehicleQuery(identifier, true));
	}

	private Optional<ServiceVehicle> vehicleQuery(String identifier, boolean lock) {
		return this.jdbc
				.sql("SELECT " + VEHICLE_COLUMNS + " FROM service_vehicles WHERE identifier = :identifier"
						+ (lock ? " FOR UPDATE" : ""))
				.param("identifier", NaturalKey.identifier(identifier)).query(JdbcFleetRepository::vehicle).optional();
	}

	@Override
	public Optional<VehicleStatus> findCurrentStatus(String identifier) {
		return this.jdbc.sql("""
				SELECT identifier, service_vehicle_type_code, current_status, current_tail_number, current_task_id
				FROM service_vehicle_current_status WHERE identifier = :identifier
				""").param("identifier", NaturalKey.identifier(identifier)).query(JdbcFleetRepository::vehicleStatus)
				.optional();
	}

	@Override
	public RepositoryPage<ServiceVehicle> findVehicles(VehicleFilter filter, RepositoryPageRequest page) {
		String order = VEHICLE_SORTS.get(page.requireAllowedSort(VEHICLE_SORTS.keySet()));
		QueryParts query = vehicleFilters(filter, "");
		long total = this.jdbc.sql("SELECT count(*) FROM service_vehicles" + query.where()).params(query.params())
				.query(Long.class).single();
		query.params().put("limit", page.limit());
		query.params().put("offset", page.offset());
		String sql = "SELECT " + VEHICLE_COLUMNS + " FROM service_vehicles" + query.where() + " ORDER BY " + order + " "
				+ page.direction() + ", identifier ASC LIMIT :limit OFFSET :offset";
		return new RepositoryPage<>(
				this.jdbc.sql(sql).params(query.params()).query(JdbcFleetRepository::vehicle).list(), page.offset(),
				page.limit(), total);
	}

	@Override
	public RepositoryPage<VehicleStatus> findCurrentStatuses(VehicleFilter filter, RepositoryPageRequest page) {
		String order = STATUS_SORTS.get(page.requireAllowedSort(STATUS_SORTS.keySet()));
		QueryParts query = vehicleFilters(filter, "v.");
		String from = " FROM service_vehicle_current_status s JOIN service_vehicles v ON v.identifier = s.identifier";
		long total = this.jdbc.sql("SELECT count(*)" + from + query.where()).params(query.params()).query(Long.class)
				.single();
		query.params().put("limit", page.limit());
		query.params().put("offset", page.offset());
		String sql = "SELECT s.identifier, s.service_vehicle_type_code, s.current_status, s.current_tail_number, s.current_task_id"
				+ from + query.where() + " ORDER BY " + order + " " + page.direction()
				+ ", s.identifier ASC LIMIT :limit OFFSET :offset";
		return new RepositoryPage<>(
				this.jdbc.sql(sql).params(query.params()).query(JdbcFleetRepository::vehicleStatus).list(),
				page.offset(), page.limit(), total);
	}

	private static QueryParts vehicleFilters(VehicleFilter filter, String prefix) {
		Map<String, Object> params = new LinkedHashMap<>();
		List<String> predicates = new ArrayList<>();
		if (filter.active() != null) {
			predicates.add(prefix + "is_active = :active");
			params.put("active", filter.active());
		}
		if (filter.operationalStatus() != null) {
			predicates.add(prefix + "operational_status = CAST(:status AS operational_status)");
			params.put("status", filter.operationalStatus().name());
		}
		if (filter.vehicleTypeCode() != null) {
			predicates.add(prefix + "service_vehicle_type_code = :type");
			params.put("type", filter.vehicleTypeCode());
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

	private static ServiceVehicle vehicle(ResultSet row, int ignored) throws SQLException {
		return new ServiceVehicle(row.getString("identifier"), row.getString("service_vehicle_type_code"),
				OperationalStatus.valueOf(row.getString("operational_status")), row.getString("notes"),
				row.getBoolean("is_active"), audit(row));
	}

	private static VehicleStatus vehicleStatus(ResultSet row, int ignored) throws SQLException {
		return new VehicleStatus(row.getString("identifier"), row.getString("service_vehicle_type_code"),
				VehicleCurrentState.valueOf(row.getString("current_status")), row.getString("current_tail_number"),
				nullableLong(row, "current_task_id"));
	}

	private static Long nullableLong(ResultSet row, String column) throws SQLException {
		long value = row.getLong(column);
		return row.wasNull() ? null : value;
	}

	private static AuditMetadata audit(ResultSet row) throws SQLException {
		return new AuditMetadata(row.getObject("created_at", OffsetDateTime.class).toInstant(),
				row.getObject("updated_at", OffsetDateTime.class).toInstant());
	}

	private record QueryParts(String where, Map<String, Object> params) {
	}
}
