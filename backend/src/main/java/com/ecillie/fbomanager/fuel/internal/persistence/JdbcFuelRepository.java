package com.ecillie.fbomanager.fuel.internal.persistence;

import com.ecillie.fbomanager.fuel.api.FuelModels.FuelLedgerEntry;
import com.ecillie.fbomanager.fuel.api.FuelModels.FuelTank;
import com.ecillie.fbomanager.fuel.api.FuelModels.FuelTransactionType;
import com.ecillie.fbomanager.fuel.api.FuelModels.FuelType;
import com.ecillie.fbomanager.fuel.api.FuelModels.LedgerFilter;
import com.ecillie.fbomanager.fuel.api.FuelModels.TankBalance;
import com.ecillie.fbomanager.fuel.api.FuelModels.TruckBalance;
import com.ecillie.fbomanager.fuel.api.FuelRepository;
import com.ecillie.fbomanager.platform.api.AuditMetadata;
import com.ecillie.fbomanager.platform.api.FixedPrecisionQuantity;
import com.ecillie.fbomanager.platform.api.NaturalKey;
import com.ecillie.fbomanager.platform.api.PersistenceExceptionMapper;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
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
public class JdbcFuelRepository implements FuelRepository {

	private static final String LEDGER_COLUMNS = "fuel_transaction_id, fuel_type_code, fuel_tank_name, fuel_truck_identifier, service_request_id, recorded_by_worker_id, transaction_type, quantity_delta, quantity_unit, transfer_group_id, occurred_at, notes, created_at";
	private static final Map<String, String> SORTS = Map.of("fuelTransactionId", "fuel_transaction_id", "occurredAt",
			"occurred_at", "fuelTankName", "fuel_tank_name", "fuelTruckIdentifier", "fuel_truck_identifier",
			"transactionType", "transaction_type");
	private static final Map<String, String> TANK_SORTS = Map.of("name", "name", "fuelTypeCode", "fuel_type_code",
			"currentQuantity", "current_quantity");
	private static final Map<String, String> TRUCK_SORTS = Map.of("identifier", "service_vehicle_identifier",
			"fuelTypeCode", "fuel_type_code", "currentQuantity", "current_quantity");
	private final JdbcClient jdbc;
	private final PersistenceExceptionMapper failures;

	public JdbcFuelRepository(JdbcClient jdbc, PersistenceExceptionMapper failures) {
		this.jdbc = jdbc;
		this.failures = failures;
	}

	@Override
	public FuelType saveType(FuelType type) {
		return translated(() -> this.jdbc.sql("""
				INSERT INTO fuel_types (code, name, default_unit, is_active) VALUES (:code, :name, :unit, :active)
				ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, default_unit = EXCLUDED.default_unit,
				    is_active = EXCLUDED.is_active
				RETURNING code, name, default_unit, is_active, created_at, updated_at
				""").param("code", type.code()).param("name", type.name()).param("unit", type.defaultUnit())
				.param("active", type.active()).query((row, ignored) -> new FuelType(row.getString("code"),
						row.getString("name"), row.getString("default_unit"), row.getBoolean("is_active"), audit(row)))
				.single());
	}

	@Override
	public Optional<FuelType> findType(String code) {
		return this.jdbc.sql("""
				SELECT code, name, default_unit, is_active, created_at, updated_at
				FROM fuel_types WHERE code = :code
				""").param("code", NaturalKey.code(code)).query((row, ignored) -> new FuelType(row.getString("code"),
				row.getString("name"), row.getString("default_unit"), row.getBoolean("is_active"), audit(row)))
				.optional();
	}

	@Override
	public FuelTank saveTank(FuelTank tank) {
		return translated(() -> this.jdbc.sql("""
				INSERT INTO fuel_tanks (name, fuel_type_code, capacity, quantity_unit, notes, is_active)
				VALUES (:name, :fuel, :capacity, :unit, :notes, :active)
				ON CONFLICT (name) DO UPDATE SET fuel_type_code = EXCLUDED.fuel_type_code,
				    capacity = EXCLUDED.capacity, quantity_unit = EXCLUDED.quantity_unit, notes = EXCLUDED.notes,
				    is_active = EXCLUDED.is_active
				RETURNING name, fuel_type_code, capacity, quantity_unit, notes, is_active, created_at, updated_at
				""").param("name", tank.name()).param("fuel", tank.fuelTypeCode())
				.param("capacity", tank.capacity().toBigDecimal()).param("unit", tank.quantityUnit())
				.param("notes", tank.notes()).param("active", tank.active())
				.query((row, ignored) -> new FuelTank(row.getString("name"), row.getString("fuel_type_code"),
						FixedPrecisionQuantity.from(row.getBigDecimal("capacity")), row.getString("quantity_unit"),
						row.getString("notes"), row.getBoolean("is_active"), audit(row)))
				.single());
	}

	@Override
	public Optional<FuelTank> findTank(String name) {
		return this.jdbc.sql("""
				SELECT name, fuel_type_code, capacity, quantity_unit, notes, is_active, created_at, updated_at
				FROM fuel_tanks WHERE name = :name
				""").param("name", NaturalKey.name(name))
				.query((row, ignored) -> new FuelTank(row.getString("name"), row.getString("fuel_type_code"),
						FixedPrecisionQuantity.from(row.getBigDecimal("capacity")), row.getString("quantity_unit"),
						row.getString("notes"), row.getBoolean("is_active"), audit(row)))
				.optional();
	}

	@Override
	public FuelLedgerEntry append(FuelLedgerEntry entry) {
		if (entry.fuelTransactionId() != null || entry.createdAt() != null) {
			throw new IllegalArgumentException(
					"fuel ledger entries are append-only and must not supply database fields");
		}
		return translated(() -> this.jdbc.sql("""
				INSERT INTO fuel_inventory_transactions (fuel_type_code, fuel_tank_name, fuel_truck_identifier,
				    service_request_id, recorded_by_worker_id, transaction_type, quantity_delta, quantity_unit,
				    transfer_group_id, occurred_at, notes)
				VALUES (:fuel, :tank, :truck, :request, :worker, CAST(:type AS fuel_transaction_type), :quantity,
				    :unit, :transfer, :occurred, :notes) RETURNING %s
				""".formatted(LEDGER_COLUMNS)).param("fuel", entry.fuelTypeCode()).param("tank", entry.fuelTankName())
				.param("truck", entry.fuelTruckIdentifier()).param("request", entry.serviceRequestId())
				.param("worker", entry.recordedByWorkerId()).param("type", entry.transactionType().name())
				.param("quantity", entry.quantityDelta().toBigDecimal()).param("unit", entry.quantityUnit())
				.param("transfer", entry.transferGroupId()).param("occurred", timestamp(entry.occurredAt()))
				.param("notes", entry.notes()).query(JdbcFuelRepository::ledgerEntry).single());
	}

	@Override
	public long nextTransferGroupId() {
		return this.jdbc.sql("SELECT nextval('fuel_transfer_group_id_seq')").query(Long.class).single();
	}

	@Override
	public Optional<TankBalance> findTankBalance(String tankName) {
		return this.jdbc.sql("SELECT * FROM fuel_tank_balances WHERE name = :name")
				.param("name", NaturalKey.name(tankName)).query(JdbcFuelRepository::tankBalance).optional();
	}

	@Override
	public Optional<TruckBalance> findTruckBalance(String identifier) {
		return this.jdbc.sql("SELECT * FROM fuel_truck_balances WHERE service_vehicle_identifier = :identifier")
				.param("identifier", NaturalKey.identifier(identifier)).query(JdbcFuelRepository::truckBalance)
				.optional();
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<TankBalance> lockTankBalance(String tankName) {
		return translated(() -> {
			String normalized = NaturalKey.name(tankName);
			if (this.jdbc.sql("SELECT name FROM fuel_tanks WHERE name = :name FOR UPDATE").param("name", normalized)
					.query(String.class).optional().isEmpty()) {
				return Optional.empty();
			}
			return findTankBalance(normalized);
		});
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<TruckBalance> lockTruckBalance(String identifier) {
		return translated(() -> {
			String normalized = NaturalKey.identifier(identifier);
			if (this.jdbc.sql(
					"SELECT service_vehicle_identifier FROM fuel_trucks WHERE service_vehicle_identifier = :identifier FOR UPDATE")
					.param("identifier", normalized).query(String.class).optional().isEmpty()) {
				return Optional.empty();
			}
			return findTruckBalance(normalized);
		});
	}

	@Override
	public RepositoryPage<TankBalance> findTankBalances(RepositoryPageRequest page) {
		String order = TANK_SORTS.get(page.requireAllowedSort(TANK_SORTS.keySet()));
		long total = this.jdbc.sql("SELECT count(*) FROM fuel_tank_balances").query(Long.class).single();
		String sql = "SELECT * FROM fuel_tank_balances ORDER BY " + order + " " + page.direction()
				+ ", name ASC LIMIT :limit OFFSET :offset";
		return new RepositoryPage<>(this.jdbc.sql(sql).param("limit", page.limit()).param("offset", page.offset())
				.query(JdbcFuelRepository::tankBalance).list(), page.offset(), page.limit(), total);
	}

	@Override
	public RepositoryPage<TruckBalance> findTruckBalances(RepositoryPageRequest page) {
		String order = TRUCK_SORTS.get(page.requireAllowedSort(TRUCK_SORTS.keySet()));
		long total = this.jdbc.sql("SELECT count(*) FROM fuel_truck_balances").query(Long.class).single();
		String sql = "SELECT * FROM fuel_truck_balances ORDER BY " + order + " " + page.direction()
				+ ", service_vehicle_identifier ASC LIMIT :limit OFFSET :offset";
		return new RepositoryPage<>(this.jdbc.sql(sql).param("limit", page.limit()).param("offset", page.offset())
				.query(JdbcFuelRepository::truckBalance).list(), page.offset(), page.limit(), total);
	}

	@Override
	public RepositoryPage<FuelLedgerEntry> findLedger(LedgerFilter filter, RepositoryPageRequest page) {
		String order = SORTS.get(page.requireAllowedSort(SORTS.keySet()));
		Map<String, Object> params = new LinkedHashMap<>();
		List<String> predicates = new ArrayList<>();
		if (filter.fuelTankName() != null) {
			predicates.add("fuel_tank_name = :tank");
			params.put("tank", filter.fuelTankName());
		}
		if (filter.fuelTruckIdentifier() != null) {
			predicates.add("fuel_truck_identifier = :truck");
			params.put("truck", filter.fuelTruckIdentifier());
		}
		if (filter.serviceRequestId() != null) {
			predicates.add("service_request_id = :request");
			params.put("request", filter.serviceRequestId());
		}
		if (filter.occurredFrom() != null) {
			predicates.add("occurred_at >= :from");
			params.put("from", timestamp(filter.occurredFrom()));
		}
		if (filter.occurredBefore() != null) {
			predicates.add("occurred_at < :before");
			params.put("before", timestamp(filter.occurredBefore()));
		}
		String where = predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
		long total = this.jdbc.sql("SELECT count(*) FROM fuel_inventory_transactions" + where).params(params)
				.query(Long.class).single();
		params.put("limit", page.limit());
		params.put("offset", page.offset());
		String sql = "SELECT " + LEDGER_COLUMNS + " FROM fuel_inventory_transactions" + where + " ORDER BY " + order
				+ " " + page.direction() + ", fuel_transaction_id ASC LIMIT :limit OFFSET :offset";
		return new RepositoryPage<>(this.jdbc.sql(sql).params(params).query(JdbcFuelRepository::ledgerEntry).list(),
				page.offset(), page.limit(), total);
	}

	private <T> T translated(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (RuntimeException failure) {
			throw this.failures.map(failure);
		}
	}

	private static FuelLedgerEntry ledgerEntry(ResultSet row, int ignored) throws SQLException {
		return new FuelLedgerEntry(row.getLong("fuel_transaction_id"), row.getString("fuel_type_code"),
				row.getString("fuel_tank_name"), row.getString("fuel_truck_identifier"),
				nullableLong(row, "service_request_id"), row.getLong("recorded_by_worker_id"),
				FuelTransactionType.valueOf(row.getString("transaction_type")),
				FixedPrecisionQuantity.from(row.getBigDecimal("quantity_delta")), row.getString("quantity_unit"),
				nullableLong(row, "transfer_group_id"), instant(row, "occurred_at"), row.getString("notes"),
				instant(row, "created_at"));
	}

	private static TankBalance tankBalance(ResultSet row, int ignored) throws SQLException {
		return new TankBalance(row.getString("name"), row.getString("fuel_type_code"),
				FixedPrecisionQuantity.from(row.getBigDecimal("capacity")), row.getString("quantity_unit"),
				FixedPrecisionQuantity.from(row.getBigDecimal("current_quantity")),
				FixedPrecisionQuantity.from(row.getBigDecimal("available_capacity")));
	}

	private static TruckBalance truckBalance(ResultSet row, int ignored) throws SQLException {
		return new TruckBalance(row.getString("service_vehicle_identifier"), row.getString("fuel_type_code"),
				FixedPrecisionQuantity.from(row.getBigDecimal("capacity")), row.getString("quantity_unit"),
				FixedPrecisionQuantity.from(row.getBigDecimal("current_quantity")),
				FixedPrecisionQuantity.from(row.getBigDecimal("available_capacity")));
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
