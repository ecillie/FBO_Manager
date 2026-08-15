package com.ecillie.fbomanager.aircraft.internal.persistence;

import com.ecillie.fbomanager.aircraft.api.AircraftModels.Aircraft;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftCategory;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftFilter;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftManufacturer;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftModel;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftModelKey;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftOperationType;
import com.ecillie.fbomanager.aircraft.api.AircraftRepository;
import com.ecillie.fbomanager.platform.api.AuditMetadata;
import com.ecillie.fbomanager.platform.api.NaturalKey;
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
public class JdbcAircraftRepository implements AircraftRepository {

	private static final String AIRCRAFT_COLUMNS = "tail_number, manufacturer_name, model_name, aircraft_operation_type_code, fuel_type_code, owner_customer_id, operator_customer_id, notes, is_active, created_at, updated_at";
	private static final String MODEL_COLUMNS = "manufacturer_name, model_name, aircraft_category_code, icao_type_code, is_active, created_at, updated_at";
	private static final Map<String, String> SORTS = Map.of("tailNumber", "a.tail_number", "manufacturerName",
			"a.manufacturer_name", "modelName", "a.model_name", "updatedAt", "a.updated_at");
	private final JdbcClient jdbc;
	private final PersistenceExceptionMapper failures;

	public JdbcAircraftRepository(JdbcClient jdbc, PersistenceExceptionMapper failures) {
		this.jdbc = jdbc;
		this.failures = failures;
	}

	@Override
	public AircraftCategory saveCategory(AircraftCategory category) {
		return translated(() -> this.jdbc.sql("""
				INSERT INTO aircraft_categories (code, name, description) VALUES (:code, :name, :description)
				ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description
				RETURNING code, name, description, created_at, updated_at
				""").param("code", category.code()).param("name", category.name())
				.param("description", category.description())
				.query((row, ignored) -> new AircraftCategory(row.getString("code"), row.getString("name"),
						row.getString("description"), audit(row)))
				.single());
	}

	@Override
	public AircraftOperationType saveOperationType(AircraftOperationType type) {
		return translated(() -> this.jdbc.sql("""
				INSERT INTO aircraft_operation_types (code, name, description, is_active)
				VALUES (:code, :name, :description, :active)
				ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description,
				    is_active = EXCLUDED.is_active
				RETURNING code, name, description, is_active, created_at, updated_at
				""").param("code", type.code()).param("name", type.name()).param("description", type.description())
				.param("active", type.active()).query((row, ignored) -> new AircraftOperationType(row.getString("code"),
						row.getString("name"), row.getString("description"), row.getBoolean("is_active"), audit(row)))
				.single());
	}

	@Override
	public AircraftManufacturer saveManufacturer(AircraftManufacturer manufacturer) {
		return translated(() -> this.jdbc.sql("""
				INSERT INTO aircraft_manufacturers (name) VALUES (:name)
				ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
				RETURNING name, created_at, updated_at
				""").param("name", manufacturer.name())
				.query((row, ignored) -> new AircraftManufacturer(row.getString("name"), audit(row))).single());
	}

	@Override
	public AircraftModel saveModel(AircraftModel model) {
		return translated(() -> this.jdbc
				.sql("""
						INSERT INTO aircraft_models (manufacturer_name, model_name, aircraft_category_code, icao_type_code, is_active)
						VALUES (:manufacturer, :model, :category, :icao, :active)
						ON CONFLICT (manufacturer_name, model_name) DO UPDATE
						SET aircraft_category_code = EXCLUDED.aircraft_category_code, icao_type_code = EXCLUDED.icao_type_code,
						    is_active = EXCLUDED.is_active RETURNING %s
						"""
						.formatted(MODEL_COLUMNS))
				.param("manufacturer", model.key().manufacturerName()).param("model", model.key().modelName())
				.param("category", model.aircraftCategoryCode()).param("icao", model.icaoTypeCode())
				.param("active", model.active()).query(JdbcAircraftRepository::aircraftModel).single());
	}

	@Override
	public Optional<AircraftModel> findModel(AircraftModelKey key) {
		return this.jdbc
				.sql("SELECT " + MODEL_COLUMNS
						+ " FROM aircraft_models WHERE manufacturer_name = :manufacturer AND model_name = :model")
				.param("manufacturer", key.manufacturerName()).param("model", key.modelName())
				.query(JdbcAircraftRepository::aircraftModel).optional();
	}

	@Override
	public List<AircraftModel> findModelsByCategory(String categoryCode) {
		return this.jdbc.sql("SELECT " + MODEL_COLUMNS
				+ " FROM aircraft_models WHERE aircraft_category_code = :category ORDER BY manufacturer_name, model_name")
				.param("category", NaturalKey.code(categoryCode)).query(JdbcAircraftRepository::aircraftModel).list();
	}

	@Override
	public Aircraft saveAircraft(Aircraft aircraft) {
		return translated(() -> this.jdbc
				.sql("""
						INSERT INTO aircraft (tail_number, manufacturer_name, model_name, aircraft_operation_type_code,
						    fuel_type_code, owner_customer_id, operator_customer_id, notes, is_active)
						VALUES (:tail, :manufacturer, :model, :operation, :fuel, :owner, :operator, :notes, :active)
						ON CONFLICT (tail_number) DO UPDATE SET manufacturer_name = EXCLUDED.manufacturer_name,
						    model_name = EXCLUDED.model_name, aircraft_operation_type_code = EXCLUDED.aircraft_operation_type_code,
						    fuel_type_code = EXCLUDED.fuel_type_code, owner_customer_id = EXCLUDED.owner_customer_id,
						    operator_customer_id = EXCLUDED.operator_customer_id, notes = EXCLUDED.notes,
						    is_active = EXCLUDED.is_active RETURNING %s
						"""
						.formatted(AIRCRAFT_COLUMNS))
				.param("tail", aircraft.tailNumber()).param("manufacturer", aircraft.modelKey().manufacturerName())
				.param("model", aircraft.modelKey().modelName()).param("operation", aircraft.operationTypeCode())
				.param("fuel", aircraft.fuelTypeCode()).param("owner", aircraft.ownerCustomerId())
				.param("operator", aircraft.operatorCustomerId()).param("notes", aircraft.notes())
				.param("active", aircraft.active()).query(JdbcAircraftRepository::aircraft).single());
	}

	@Override
	public Optional<Aircraft> findAircraft(String tailNumber) {
		return aircraftQuery(tailNumber, false);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Aircraft> lockAircraft(String tailNumber) {
		return translated(() -> aircraftQuery(tailNumber, true));
	}

	private Optional<Aircraft> aircraftQuery(String tailNumber, boolean lock) {
		return this.jdbc
				.sql("SELECT " + AIRCRAFT_COLUMNS + " FROM aircraft WHERE tail_number = :tail"
						+ (lock ? " FOR UPDATE" : ""))
				.param("tail", NaturalKey.identifier(tailNumber)).query(JdbcAircraftRepository::aircraft).optional();
	}

	@Override
	public RepositoryPage<Aircraft> findAircraft(AircraftFilter filter, RepositoryPageRequest page) {
		String order = SORTS.get(page.requireAllowedSort(SORTS.keySet()));
		Map<String, Object> params = new LinkedHashMap<>();
		List<String> predicates = new ArrayList<>();
		if (filter.active() != null) {
			predicates.add("a.is_active = :active");
			params.put("active", filter.active());
		}
		if (filter.operationTypeCode() != null) {
			predicates.add("a.aircraft_operation_type_code = :operation");
			params.put("operation", filter.operationTypeCode());
		}
		if (filter.categoryCode() != null) {
			predicates.add("m.aircraft_category_code = :category");
			params.put("category", filter.categoryCode());
		}
		if (filter.query() != null) {
			predicates.add(
					"(a.tail_number ILIKE :query OR a.manufacturer_name ILIKE :query OR a.model_name ILIKE :query)");
			params.put("query", "%" + filter.query() + "%");
		}
		String from = " FROM aircraft a JOIN aircraft_models m ON m.manufacturer_name = a.manufacturer_name AND m.model_name = a.model_name";
		String where = predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
		long total = this.jdbc.sql("SELECT count(*)" + from + where).params(params).query(Long.class).single();
		params.put("limit", page.limit());
		params.put("offset", page.offset());
		String sql = "SELECT " + prefixedAircraftColumns() + from + where + " ORDER BY " + order + " "
				+ page.direction() + ", a.tail_number ASC LIMIT :limit OFFSET :offset";
		return new RepositoryPage<>(this.jdbc.sql(sql).params(params).query(JdbcAircraftRepository::aircraft).list(),
				page.offset(), page.limit(), total);
	}

	private <T> T translated(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (RuntimeException failure) {
			throw this.failures.map(failure);
		}
	}

	private static String prefixedAircraftColumns() {
		return AIRCRAFT_COLUMNS.replaceAll("(^|, )([a-z_]+)", "$1a.$2");
	}

	private static AircraftModel aircraftModel(ResultSet row, int ignored) throws SQLException {
		return new AircraftModel(new AircraftModelKey(row.getString("manufacturer_name"), row.getString("model_name")),
				row.getString("aircraft_category_code"), row.getString("icao_type_code"), row.getBoolean("is_active"),
				audit(row));
	}

	private static Aircraft aircraft(ResultSet row, int ignored) throws SQLException {
		return new Aircraft(row.getString("tail_number"),
				new AircraftModelKey(row.getString("manufacturer_name"), row.getString("model_name")),
				row.getString("aircraft_operation_type_code"), row.getString("fuel_type_code"),
				nullableLong(row, "owner_customer_id"), nullableLong(row, "operator_customer_id"),
				row.getString("notes"), row.getBoolean("is_active"), audit(row));
	}

	private static Long nullableLong(ResultSet row, String column) throws SQLException {
		long value = row.getLong(column);
		return row.wasNull() ? null : value;
	}

	private static AuditMetadata audit(ResultSet row) throws SQLException {
		return new AuditMetadata(row.getObject("created_at", OffsetDateTime.class).toInstant(),
				row.getObject("updated_at", OffsetDateTime.class).toInstant());
	}
}
