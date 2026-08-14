package com.ecillie.fbomanager.parking.internal.persistence;

import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingArea;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingAreaPreference;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingSpot;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingSpotFilter;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingSpotPreference;
import com.ecillie.fbomanager.parking.api.ParkingRepository;
import com.ecillie.fbomanager.platform.api.AuditMetadata;
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
public class JdbcParkingRepository implements ParkingRepository {

	private static final String AREA_COLUMNS = "area_code, parent_area_code, name, notes, is_active, created_at, updated_at";
	private static final String SPOT_COLUMNS = "spot_code, parking_area_code, name, operational_status, notes, created_at, updated_at";
	private static final Map<String, String> SPOT_SORTS = Map.of("spotCode", "spot_code", "name", "name",
			"parkingAreaCode", "parking_area_code", "operationalStatus", "operational_status");
	private final JdbcClient jdbc;
	private final PersistenceExceptionMapper failures;

	public JdbcParkingRepository(JdbcClient jdbc, PersistenceExceptionMapper failures) {
		this.jdbc = jdbc;
		this.failures = failures;
	}

	@Override
	public ParkingArea saveArea(ParkingArea area) {
		return translated(() -> this.jdbc.sql("""
				INSERT INTO parking_areas (area_code, parent_area_code, name, notes, is_active)
				VALUES (:code, :parent, :name, :notes, :active)
				ON CONFLICT (area_code) DO UPDATE SET parent_area_code = EXCLUDED.parent_area_code,
				    name = EXCLUDED.name, notes = EXCLUDED.notes, is_active = EXCLUDED.is_active
				RETURNING %s
				""".formatted(AREA_COLUMNS)).param("code", area.areaCode()).param("parent", area.parentAreaCode())
				.param("name", area.name()).param("notes", area.notes()).param("active", area.active())
				.query(JdbcParkingRepository::area).single());
	}

	@Override
	public ParkingSpot saveSpot(ParkingSpot spot) {
		return translated(() -> this.jdbc.sql("""
				INSERT INTO parking_spots (spot_code, parking_area_code, name, operational_status, notes)
				VALUES (:code, :area, :name, CAST(:status AS operational_status), :notes)
				ON CONFLICT (spot_code) DO UPDATE SET parking_area_code = EXCLUDED.parking_area_code,
				    name = EXCLUDED.name, operational_status = EXCLUDED.operational_status, notes = EXCLUDED.notes
				RETURNING %s
				""".formatted(SPOT_COLUMNS)).param("code", spot.spotCode()).param("area", spot.parkingAreaCode())
				.param("name", spot.name()).param("status", spot.status().name()).param("notes", spot.notes())
				.query(JdbcParkingRepository::spot).single());
	}

	@Override
	public ParkingAreaPreference saveAreaPreference(ParkingAreaPreference preference) {
		return translated(() -> this.jdbc.sql("""
				INSERT INTO parking_area_preferences (parking_area_code, aircraft_category_code, preference_rank)
				VALUES (:area, :category, :rank)
				ON CONFLICT (parking_area_code, aircraft_category_code) DO UPDATE
				SET preference_rank = EXCLUDED.preference_rank
				RETURNING parking_area_code, aircraft_category_code, preference_rank, created_at, updated_at
				""").param("area", preference.parkingAreaCode()).param("category", preference.aircraftCategoryCode())
				.param("rank", preference.preferenceRank())
				.query((row, ignored) -> new ParkingAreaPreference(row.getString("parking_area_code"),
						row.getString("aircraft_category_code"), row.getShort("preference_rank"), audit(row)))
				.single());
	}

	@Override
	public ParkingSpotPreference saveSpotPreference(ParkingSpotPreference preference) {
		return translated(() -> this.jdbc.sql("""
				INSERT INTO parking_spot_preferences (parking_spot_code, aircraft_category_code, preference_rank)
				VALUES (:spot, :category, :rank)
				ON CONFLICT (parking_spot_code, aircraft_category_code) DO UPDATE
				SET preference_rank = EXCLUDED.preference_rank
				RETURNING parking_spot_code, aircraft_category_code, preference_rank, created_at, updated_at
				""").param("spot", preference.parkingSpotCode()).param("category", preference.aircraftCategoryCode())
				.param("rank", preference.preferenceRank())
				.query((row, ignored) -> new ParkingSpotPreference(row.getString("parking_spot_code"),
						row.getString("aircraft_category_code"), row.getShort("preference_rank"), audit(row)))
				.single());
	}

	@Override
	public Optional<ParkingSpot> findSpot(String spotCode) {
		return spotQuery(spotCode, false);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<ParkingSpot> lockSpot(String spotCode) {
		return translated(() -> spotQuery(spotCode, true));
	}

	private Optional<ParkingSpot> spotQuery(String spotCode, boolean lock) {
		return this.jdbc
				.sql("SELECT " + SPOT_COLUMNS + " FROM parking_spots WHERE spot_code = :code"
						+ (lock ? " FOR UPDATE" : ""))
				.param("code", NaturalKey.code(spotCode)).query(JdbcParkingRepository::spot).optional();
	}

	@Override
	public List<ParkingArea> findAreaTree() {
		return this.jdbc
				.sql("""
						WITH RECURSIVE tree AS (
						  SELECT area.*, ARRAY[area.area_code::text] AS path FROM parking_areas area WHERE parent_area_code IS NULL
						  UNION ALL
						  SELECT child.*, tree.path || child.area_code::text FROM parking_areas child
						  JOIN tree ON child.parent_area_code = tree.area_code
						)
						SELECT area_code, parent_area_code, name, notes, is_active, created_at, updated_at
						FROM tree ORDER BY path
						""")
				.query(JdbcParkingRepository::area).list();
	}

	@Override
	public RepositoryPage<ParkingSpot> findSpots(ParkingSpotFilter filter, RepositoryPageRequest page) {
		String order = SPOT_SORTS.get(page.requireAllowedSort(SPOT_SORTS.keySet()));
		Map<String, Object> params = new LinkedHashMap<>();
		List<String> predicates = new ArrayList<>();
		if (filter.areaCode() != null) {
			predicates.add("parking_area_code = :area");
			params.put("area", filter.areaCode());
		}
		if (filter.status() != null) {
			predicates.add("operational_status = CAST(:status AS operational_status)");
			params.put("status", filter.status().name());
		}
		if (filter.categoryCode() != null) {
			predicates.add(
					"(EXISTS (SELECT 1 FROM parking_spot_preferences p WHERE p.parking_spot_code = parking_spots.spot_code AND p.aircraft_category_code = :category) OR EXISTS (SELECT 1 FROM parking_area_preferences p WHERE p.parking_area_code = parking_spots.parking_area_code AND p.aircraft_category_code = :category))");
			params.put("category", filter.categoryCode());
		}
		String where = predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
		long total = this.jdbc.sql("SELECT count(*) FROM parking_spots" + where).params(params).query(Long.class)
				.single();
		params.put("limit", page.limit());
		params.put("offset", page.offset());
		String sql = "SELECT " + SPOT_COLUMNS + " FROM parking_spots" + where + " ORDER BY " + order + " "
				+ page.direction() + ", spot_code ASC LIMIT :limit OFFSET :offset";
		return new RepositoryPage<>(this.jdbc.sql(sql).params(params).query(JdbcParkingRepository::spot).list(),
				page.offset(), page.limit(), total);
	}

	private <T> T translated(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (RuntimeException failure) {
			throw this.failures.map(failure);
		}
	}

	private static ParkingArea area(ResultSet row, int ignored) throws SQLException {
		return new ParkingArea(row.getString("area_code"), row.getString("parent_area_code"), row.getString("name"),
				row.getString("notes"), row.getBoolean("is_active"), audit(row));
	}

	private static ParkingSpot spot(ResultSet row, int ignored) throws SQLException {
		return new ParkingSpot(row.getString("spot_code"), row.getString("parking_area_code"), row.getString("name"),
				OperationalStatus.valueOf(row.getString("operational_status")), row.getString("notes"), audit(row));
	}

	private static AuditMetadata audit(ResultSet row) throws SQLException {
		return new AuditMetadata(row.getObject("created_at", OffsetDateTime.class).toInstant(),
				row.getObject("updated_at", OffsetDateTime.class).toInstant());
	}
}
