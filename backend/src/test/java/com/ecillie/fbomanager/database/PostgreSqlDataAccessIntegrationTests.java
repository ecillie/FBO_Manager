package com.ecillie.fbomanager.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecillie.fbomanager.FboManagerApplication;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = {"spring.flyway.enabled=true", "management.server.port=0"})
class PostgreSqlDataAccessIntegrationTests {

	private static final DockerImageName POSTGRES_IMAGE = DockerImageName
			.parse("postgres:18.3-alpine@sha256:54451ecb8ab38c24c3ec123f2fd501303a3a1856a5c66e98cecf2460d5e1e9d7")
			.asCompatibleSubstituteFor("postgres");
	private static final String MIGRATION_USER = "fbo_migration_test";
	private static final String MIGRATION_PASSWORD = "migration-test-password";
	private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE)
			.withDatabaseName("fbo_manager_test").withUsername(MIGRATION_USER).withPassword(MIGRATION_PASSWORD);

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private Clock clock;

	@Autowired
	@Qualifier("airportZoneId") private ZoneId airportZoneId;

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("fbo.database.url", POSTGRES::getJdbcUrl);
		registry.add("fbo.database.username", POSTGRES::getUsername);
		registry.add("fbo.database.password", POSTGRES::getPassword);
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
		registry.add("spring.flyway.user", POSTGRES::getUsername);
		registry.add("spring.flyway.password", POSTGRES::getPassword);
		registry.add("spring.flyway.placeholders.applicationRole", POSTGRES::getUsername);
	}

	@Test
	void springDataAccessComponentsShareTheManagedHikariDataSourceAndJpaTransactionManager() {
		assertThat(this.dataSource).isInstanceOf(HikariDataSource.class);
		HikariDataSource hikari = (HikariDataSource) this.dataSource;
		assertThat(hikari.getMaximumPoolSize()).isEqualTo(10);
		assertThat(hikari.getMinimumIdle()).isEqualTo(2);
		assertThat(hikari.getConnectionTimeout()).isEqualTo(30_000);

		assertThat(this.transactionManager).isInstanceOf(JpaTransactionManager.class);
		assertThat(((JpaTransactionManager) this.transactionManager).getDataSource()).isSameAs(this.dataSource);
		assertThat(this.entityManagerFactory.getProperties()).containsEntry("hibernate.hbm2ddl.auto", "validate");
		assertThat(this.clock.getZone()).isEqualTo(ZoneId.of("Z"));
		assertThat(this.airportZoneId).isEqualTo(ZoneId.of("America/New_York"));

		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
		transaction.executeWithoutResult(status -> {
			this.jdbcClient.sql("INSERT INTO customers (name) VALUES ('Rollback Probe')").update();
			Number visibleThroughJpa = (Number) this.entityManager
					.createNativeQuery("SELECT count(*) FROM customers WHERE name = 'Rollback Probe'")
					.getSingleResult();
			assertThat(visibleThroughJpa.longValue()).isOne();
			status.setRollbackOnly();
		});

		Long countAfterRollback = this.jdbcClient.sql("SELECT count(*) FROM customers WHERE name = 'Rollback Probe'")
				.query(Long.class).single();
		assertThat(countAfterRollback).isZero();
	}

	@Test
	void emptyPostgreSql18DatabaseMigratesWithoutLosingBaselineObjectsAndUsesLeastPrivilegeRole() throws Exception {
		TestDatabase database = createDatabase("empty");
		Flyway flyway = flyway(database, false);

		MigrateResult result = flyway.migrate();

		assertThat(result.success).isTrue();
		assertThat(result.migrationsExecuted).isEqualTo(4);
		assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
		try (Connection owner = database.ownerConnection()) {
			assertThat(integer(owner, "SELECT current_setting('server_version_num')::integer / 10000")).isEqualTo(18);
			assertThat(names(owner,
					"SELECT typname FROM pg_type JOIN pg_namespace ON pg_namespace.oid = pg_type.typnamespace "
							+ "WHERE pg_namespace.nspname = 'public' AND pg_type.typtype = 'e' ORDER BY typname"))
					.containsExactly("fuel_transaction_type", "operational_status", "service_request_status",
							"task_status", "visit_status", "worker_shift_status");
			assertThat(names(owner, "SELECT viewname FROM pg_views WHERE schemaname = 'public' ORDER BY viewname"))
					.containsExactly("fuel_tank_balances", "fuel_truck_balances", "service_vehicle_current_status",
							"worker_current_status");
			assertThat(names(owner,
					"SELECT proname FROM pg_proc JOIN pg_namespace ON pg_namespace.oid = pg_proc.pronamespace "
							+ "WHERE pg_namespace.nspname = 'public' ORDER BY proname"))
					.contains("prevent_parking_area_cycle", "validate_visit_parking", "prevent_fuel_inventory_mutation",
							"validate_fuel_inventory_transaction");
			assertThat(names(owner,
					"SELECT tgname FROM pg_trigger JOIN pg_class ON pg_class.oid = pg_trigger.tgrelid "
							+ "JOIN pg_namespace ON pg_namespace.oid = pg_class.relnamespace "
							+ "WHERE pg_namespace.nspname = 'public' AND NOT tgisinternal ORDER BY tgname"))
					.contains("aircraft_visits_validate_parking", "fuel_inventory_transactions_append_only");
			assertThat(string(owner,
					"SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' "
							+ "AND indexname = 'aircraft_visits_one_active_per_aircraft_uq'"))
					.contains("WHERE", "status");
			assertThat(integer(owner,
					"SELECT count(*) FROM information_schema.table_constraints WHERE constraint_schema = 'public' "
							+ "AND constraint_name IN ('fuel_inventory_transactions_one_holder_ck', "
							+ "'idempotency_records_retention_ck')"))
					.isEqualTo(2);
			assertReferenceData(owner);
		}

		try (Connection application = database.applicationConnection()) {
			assertThat(bool(application, "SELECT has_schema_privilege(current_user, 'public', 'CREATE')")).isFalse();
			assertThat(bool(application,
					"SELECT has_table_privilege(current_user, 'public.customers', 'SELECT, INSERT, UPDATE, DELETE')"))
					.isTrue();
			assertThat(bool(application,
					"SELECT has_table_privilege(current_user, 'public.flyway_schema_history', 'SELECT')")).isFalse();
			assertThat(bool(application,
					"SELECT has_table_privilege(current_user, 'public.fuel_inventory_transactions', 'UPDATE')"))
					.isFalse();
			try (Statement statement = application.createStatement()) {
				assertThat(statement.executeUpdate("INSERT INTO customers (name) VALUES ('Application Grant Probe')"))
						.isOne();
				assertThatThrownBy(() -> statement.execute("CREATE TABLE forbidden_schema_change (id integer)"))
						.isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
			}
		}
	}

	@Test
	void committedBaselineFixtureUpgradesAndReferenceSeedCanRerunWithoutChangingState() throws Exception {
		TestDatabase database = createDatabase("upgrade");
		try (Connection owner = database.ownerConnection(); Statement statement = owner.createStatement()) {
			statement.execute(normalizedBaseline());
		}

		Flyway flyway = flyway(database, true);
		MigrateResult result = flyway.migrate();

		assertThat(result.success).isTrue();
		assertThat(result.migrationsExecuted).isEqualTo(3);
		assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

		try (Connection owner = database.ownerConnection()) {
			assertThat(names(owner,
					"SELECT version FROM flyway_schema_history "
							+ "WHERE success AND version IS NOT NULL ORDER BY installed_rank"))
					.containsExactly("1", "2", "3");
			assertThat(bool(owner, "SELECT to_regclass('public.idempotency_records') IS NOT NULL")).isTrue();
			Map<String, String> before = referenceDataSnapshot(owner);
			ScriptUtils.executeSqlScript(owner, new ClassPathResource("db/migration/R__reference_data.sql"));
			Map<String, String> after = referenceDataSnapshot(owner);
			assertThat(after).isEqualTo(before);
		}
	}

	@Test
	void flywayV1IsTheCommittedHistoricalBaselineConvertedFromPsqlToFlyway() throws Exception {
		String migration = new ClassPathResource("db/migration/V1__baseline_schema.sql")
				.getContentAsString(StandardCharsets.UTF_8);

		assertThat(migration).isEqualTo(normalizedBaseline());
	}

	@Test
	void oneShotMigrationProfileValidatesThePackagedPathAndReturns() {
		FboManagerApplication.main(new String[]{"--spring.profiles.active=migrate",
				"--fbo.database.url=" + POSTGRES.getJdbcUrl(), "--fbo.database.username=" + POSTGRES.getUsername(),
				"--fbo.database.password=" + POSTGRES.getPassword(), "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
				"--spring.datasource.username=" + POSTGRES.getUsername(),
				"--spring.datasource.password=" + POSTGRES.getPassword(),
				"--spring.flyway.url=" + POSTGRES.getJdbcUrl(), "--spring.flyway.user=" + POSTGRES.getUsername(),
				"--spring.flyway.password=" + POSTGRES.getPassword(),
				"--spring.flyway.placeholders.applicationRole=" + POSTGRES.getUsername()});

		assertThat(this.jdbcClient.sql("SELECT count(*) FROM flyway_schema_history WHERE success").query(Long.class)
				.single()).isEqualTo(4);
	}

	private static String normalizedBaseline() throws Exception {
		String baseline = new ClassPathResource("db/baseline/001_schema.sql")
				.getContentAsString(StandardCharsets.UTF_8);
		return baseline.replaceFirst("\\\\set ON_ERROR_STOP on\\R+BEGIN;\\R+", "").replaceFirst("\\R+COMMIT;\\R?$",
				"\n");
	}

	private static synchronized TestDatabase createDatabase(String purpose) throws SQLException {
		int sequence = DATABASE_SEQUENCE.incrementAndGet();
		String databaseName = "fbo_" + purpose + "_" + sequence;
		String applicationRole = "fbo_app_" + purpose + "_" + sequence;
		String applicationPassword = "application-test-password-" + sequence;
		try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
				POSTGRES.getPassword()); Statement statement = admin.createStatement()) {
			statement.execute("CREATE ROLE " + applicationRole + " LOGIN PASSWORD '" + applicationPassword + "'");
			statement.execute("CREATE DATABASE " + databaseName + " OWNER " + POSTGRES.getUsername());
		}
		return new TestDatabase(jdbcUrl(databaseName), applicationRole, applicationPassword);
	}

	private static Flyway flyway(TestDatabase database, boolean baselineOnMigrate) {
		return Flyway.configure().dataSource(database.jdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.locations("classpath:db/migration").placeholderReplacement(true)
				.placeholders(Map.of("applicationRole", database.applicationRole()))
				.baselineOnMigrate(baselineOnMigrate).baselineVersion("1").load();
	}

	private static String jdbcUrl(String databaseName) {
		return "jdbc:postgresql://" + POSTGRES.getHost() + ":"
				+ POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + databaseName;
	}

	private static void assertReferenceData(Connection connection) throws SQLException {
		assertThat(integer(connection, "SELECT count(*) FROM fuel_types")).isEqualTo(2);
		assertThat(integer(connection, "SELECT count(*) FROM aircraft_categories")).isEqualTo(4);
		assertThat(integer(connection, "SELECT count(*) FROM aircraft_operation_types")).isEqualTo(4);
		assertThat(integer(connection, "SELECT count(*) FROM service_types")).isEqualTo(8);
		assertThat(integer(connection, "SELECT count(*) FROM service_vehicle_types")).isEqualTo(6);
		assertThat(integer(connection, "SELECT count(*) FROM roles")).isEqualTo(6);
	}

	private static Map<String, String> referenceDataSnapshot(Connection connection) throws SQLException {
		Map<String, String> snapshot = new LinkedHashMap<>();
		snapshot.put("fuel_types", jsonRows(connection, "fuel_types", "code"));
		snapshot.put("aircraft_categories", jsonRows(connection, "aircraft_categories", "code"));
		snapshot.put("aircraft_operation_types", jsonRows(connection, "aircraft_operation_types", "code"));
		snapshot.put("service_types", jsonRows(connection, "service_types", "code"));
		snapshot.put("service_vehicle_types", jsonRows(connection, "service_vehicle_types", "code"));
		snapshot.put("roles", jsonRows(connection, "roles", "name"));
		return snapshot;
	}

	private static String jsonRows(Connection connection, String table, String key) throws SQLException {
		return string(connection, "SELECT COALESCE(jsonb_agg(to_jsonb(seed) ORDER BY " + key
				+ ")::text, '[]') FROM (SELECT * FROM " + table + ") AS seed");
	}

	private static List<String> names(Connection connection, String sql) throws SQLException {
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
			java.util.ArrayList<String> values = new java.util.ArrayList<>();
			while (result.next()) {
				values.add(result.getString(1));
			}
			return values;
		}
	}

	private static int integer(Connection connection, String sql) throws SQLException {
		return ((Number) scalar(connection, sql)).intValue();
	}

	private static boolean bool(Connection connection, String sql) throws SQLException {
		return (Boolean) scalar(connection, sql);
	}

	private static String string(Connection connection, String sql) throws SQLException {
		return (String) scalar(connection, sql);
	}

	private static Object scalar(Connection connection, String sql) throws SQLException {
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
			assertThat(result.next()).isTrue();
			return result.getObject(1);
		}
	}

	private record TestDatabase(String jdbcUrl, String applicationRole, String applicationPassword) {

		Connection ownerConnection() throws SQLException {
			return DriverManager.getConnection(this.jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
		}

		Connection applicationConnection() throws SQLException {
			return DriverManager.getConnection(this.jdbcUrl, this.applicationRole, this.applicationPassword);
		}
	}
}
