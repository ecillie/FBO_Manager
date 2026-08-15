package com.ecillie.fbomanager.administration.internal.persistence;

import com.ecillie.fbomanager.administration.api.AdministrationModels.AirportSettings;
import com.ecillie.fbomanager.administration.api.AdministrationModels.Customer;
import com.ecillie.fbomanager.administration.api.AdministrationModels.CustomerFilter;
import com.ecillie.fbomanager.administration.api.AdministrationRepository;
import com.ecillie.fbomanager.platform.api.AuditMetadata;
import com.ecillie.fbomanager.platform.api.PersistenceExceptionMapper;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdministrationRepository implements AdministrationRepository {

	private static final Map<String, String> CUSTOMER_SORTS = Map.of("customerId", "customer_id", "name", "name",
			"createdAt", "created_at", "updatedAt", "updated_at");
	private static final String CUSTOMER_COLUMNS = "customer_id, name, phone, email, notes, is_active, created_at, updated_at";
	private final JdbcClient jdbc;
	private final PersistenceExceptionMapper failures;

	public JdbcAdministrationRepository(JdbcClient jdbc, PersistenceExceptionMapper failures) {
		this.jdbc = jdbc;
		this.failures = failures;
	}

	@Override
	public Optional<AirportSettings> findAirportSettings() {
		return this.jdbc
				.sql("SELECT icao_code, name, iata_code, timezone, created_at, updated_at FROM airport_settings")
				.query(JdbcAdministrationRepository::airportSettings).optional();
	}

	@Override
	public AirportSettings saveAirportSettings(AirportSettings settings) {
		return translated(() -> this.jdbc.sql("""
				INSERT INTO airport_settings (icao_code, name, iata_code, timezone)
				VALUES (:icao, :name, :iata, :timezone)
				ON CONFLICT (icao_code) DO UPDATE SET name = EXCLUDED.name, iata_code = EXCLUDED.iata_code,
				    timezone = EXCLUDED.timezone
				RETURNING icao_code, name, iata_code, timezone, created_at, updated_at
				""").param("icao", settings.icaoCode()).param("name", settings.name())
				.param("iata", settings.iataCode()).param("timezone", settings.timezone().getId())
				.query(JdbcAdministrationRepository::airportSettings).single());
	}

	@Override
	public Optional<Customer> findCustomer(long customerId) {
		return this.jdbc.sql("SELECT " + CUSTOMER_COLUMNS + " FROM customers WHERE customer_id = :id")
				.param("id", customerId).query(JdbcAdministrationRepository::customer).optional();
	}

	@Override
	public RepositoryPage<Customer> findCustomers(CustomerFilter filter, RepositoryPageRequest page) {
		String order = CUSTOMER_SORTS.get(page.requireAllowedSort(CUSTOMER_SORTS.keySet()));
		Map<String, Object> params = new LinkedHashMap<>();
		ArrayList<String> predicates = new ArrayList<>();
		if (filter.active() != null) {
			predicates.add("is_active = :active");
			params.put("active", filter.active());
		}
		if (filter.nameContains() != null) {
			predicates.add("name ILIKE :name");
			params.put("name", "%" + filter.nameContains() + "%");
		}
		String where = predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
		long total = this.jdbc.sql("SELECT count(*) FROM customers" + where).params(params).query(Long.class).single();
		params.put("limit", page.limit());
		params.put("offset", page.offset());
		String sql = "SELECT " + CUSTOMER_COLUMNS + " FROM customers" + where + " ORDER BY " + order + " "
				+ page.direction() + ", customer_id ASC LIMIT :limit OFFSET :offset";
		return new RepositoryPage<>(
				this.jdbc.sql(sql).params(params).query(JdbcAdministrationRepository::customer).list(), page.offset(),
				page.limit(), total);
	}

	@Override
	public Customer saveCustomer(Customer customer) {
		return translated(() -> {
			JdbcClient.StatementSpec statement;
			if (customer.customerId() == null) {
				statement = this.jdbc.sql("""
						INSERT INTO customers (name, phone, email, notes, is_active)
						VALUES (:name, :phone, :email, :notes, :active)
						RETURNING %s
						""".formatted(CUSTOMER_COLUMNS));
			} else {
				statement = this.jdbc.sql("""
						UPDATE customers SET name = :name, phone = :phone, email = :email, notes = :notes,
						    is_active = :active WHERE customer_id = :id RETURNING %s
						""".formatted(CUSTOMER_COLUMNS)).param("id", customer.customerId());
			}
			return statement.param("name", customer.name()).param("phone", customer.phone())
					.param("email", customer.email()).param("notes", customer.notes())
					.param("active", customer.active()).query(JdbcAdministrationRepository::customer).optional()
					.orElseThrow(() -> new IllegalArgumentException("customer does not exist"));
		});
	}

	private <T> T translated(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (RuntimeException failure) {
			throw this.failures.map(failure);
		}
	}

	private static AirportSettings airportSettings(ResultSet row, int ignored) throws SQLException {
		return new AirportSettings(row.getString("icao_code"), row.getString("name"), row.getString("iata_code"),
				ZoneId.of(row.getString("timezone")), audit(row));
	}

	private static Customer customer(ResultSet row, int ignored) throws SQLException {
		return new Customer(row.getLong("customer_id"), row.getString("name"), row.getString("phone"),
				row.getString("email"), row.getString("notes"), row.getBoolean("is_active"), audit(row));
	}

	private static AuditMetadata audit(ResultSet row) throws SQLException {
		return new AuditMetadata(row.getObject("created_at", OffsetDateTime.class).toInstant(),
				row.getObject("updated_at", OffsetDateTime.class).toInstant());
	}
}
