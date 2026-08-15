package com.ecillie.fbomanager.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecillie.fbomanager.FboManagerApplication;
import com.ecillie.fbomanager.administration.api.AdministrationModels;
import com.ecillie.fbomanager.administration.api.AdministrationRepository;
import com.ecillie.fbomanager.aircraft.api.AircraftModels;
import com.ecillie.fbomanager.aircraft.api.AircraftRepository;
import com.ecillie.fbomanager.fleet.api.FleetModels;
import com.ecillie.fbomanager.fleet.api.FleetRepository;
import com.ecillie.fbomanager.fuel.api.FuelModels;
import com.ecillie.fbomanager.fuel.api.FuelRepository;
import com.ecillie.fbomanager.parking.api.ParkingModels;
import com.ecillie.fbomanager.parking.api.ParkingRepository;
import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.Capability;
import com.ecillie.fbomanager.platform.api.DomainConflictException;
import com.ecillie.fbomanager.platform.api.FixedPrecisionQuantity;
import com.ecillie.fbomanager.platform.api.IdempotencyKey;
import com.ecillie.fbomanager.platform.api.OperationalStatus;
import com.ecillie.fbomanager.platform.api.PersistenceFailure;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest.Direction;
import com.ecillie.fbomanager.services.api.ServiceModels;
import com.ecillie.fbomanager.services.api.ServiceRepository;
import com.ecillie.fbomanager.tasks.api.TaskModels;
import com.ecillie.fbomanager.tasks.api.TaskRepository;
import com.ecillie.fbomanager.visits.api.VisitModels;
import com.ecillie.fbomanager.visits.api.VisitRepository;
import com.ecillie.fbomanager.visits.api.VisitService;
import com.ecillie.fbomanager.workforce.api.WorkforceModels;
import com.ecillie.fbomanager.workforce.api.WorkforceRepository;
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
import java.time.Instant;
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

	@Autowired
	private AdministrationRepository administrationRepository;

	@Autowired
	private AircraftRepository aircraftRepository;

	@Autowired
	private ParkingRepository parkingRepository;

	@Autowired
	private VisitRepository visitRepository;

	@Autowired
	private VisitService visitService;

	@Autowired
	private ServiceRepository serviceRepository;

	@Autowired
	private FleetRepository fleetRepository;

	@Autowired
	private FuelRepository fuelRepository;

	@Autowired
	private WorkforceRepository workforceRepository;

	@Autowired
	private TaskRepository taskRepository;

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
	void repositoriesRoundTripTheCompleteOperationalModelAndDerivedViewsWithoutLoss() {
		Instant occurredAt = Instant.parse("2026-08-13T18:03:27.123456Z");
		RepositoryPageRequest firstPage = new RepositoryPageRequest(0, 25, "customerId", Direction.ASC);

		AdministrationModels.AirportSettings airport = this.administrationRepository
				.saveAirportSettings(new AdministrationModels.AirportSettings(" kttn ", "Repository Test Airport",
						"ttn", ZoneId.of("America/New_York"), null));
		assertThat(airport.icaoCode()).isEqualTo("KTTN");
		AdministrationModels.Customer customer = this.administrationRepository
				.saveCustomer(new AdministrationModels.Customer(null, "Repository Customer", null, "OWNER@EXAMPLE.COM",
						null, true, null));
		assertThat(customer.email()).isEqualTo("owner@example.com");
		assertThat(this.administrationRepository
				.findCustomers(new AdministrationModels.CustomerFilter(true, "Repository"), firstPage).items())
				.extracting(AdministrationModels.Customer::customerId).contains(customer.customerId());

		this.fuelRepository.saveType(new FuelModels.FuelType("test_jet_a", "Test Jet A", "us_gallon", true, null));
		this.aircraftRepository.saveCategory(new AircraftModels.AircraftCategory("test_jet", "Test Jet", null, null));
		this.aircraftRepository.saveOperationType(
				new AircraftModels.AircraftOperationType("test_charter", "Test Charter", null, true, null));
		this.aircraftRepository.saveManufacturer(new AircraftModels.AircraftManufacturer("Test Manufacturer", null));
		AircraftModels.AircraftModelKey modelKey = new AircraftModels.AircraftModelKey("Test Manufacturer", "Model 8");
		this.aircraftRepository.saveModel(new AircraftModels.AircraftModel(modelKey, "test_jet", "T008", true, null));
		AircraftModels.Aircraft aircraft = this.aircraftRepository.saveAircraft(new AircraftModels.Aircraft(" n800it ",
				modelKey, "test_charter", "test_jet_a", customer.customerId(), null, null, true, null));
		assertThat(this.aircraftRepository.findModel(modelKey)).isPresent();
		assertThat(this.aircraftRepository
				.findAircraft(new AircraftModels.AircraftFilter(true, "TEST_CHARTER", "TEST_JET", "800"),
						new RepositoryPageRequest(0, 10, "tailNumber", Direction.ASC))
				.items()).containsExactly(aircraft);

		ParkingModels.ParkingArea rootArea = this.parkingRepository
				.saveArea(new ParkingModels.ParkingArea("test_ramp", null, "Test Ramp", null, true, null));
		this.parkingRepository.saveArea(
				new ParkingModels.ParkingArea("test_ramp_east", rootArea.areaCode(), "East", null, true, null));
		ParkingModels.ParkingSpot spot = this.parkingRepository.saveSpot(new ParkingModels.ParkingSpot("test_spot_8",
				"test_ramp_east", "Spot 8", OperationalStatus.AVAILABLE, null, null));
		this.parkingRepository.saveAreaPreference(
				new ParkingModels.ParkingAreaPreference("test_ramp_east", "test_jet", (short) 2, null));
		this.parkingRepository.saveSpotPreference(
				new ParkingModels.ParkingSpotPreference(spot.spotCode(), "test_jet", (short) 1, null));
		assertThat(this.parkingRepository.findAreaTree()).extracting(ParkingModels.ParkingArea::areaCode)
				.containsSubsequence("TEST_RAMP", "TEST_RAMP_EAST");
		assertThat(this.parkingRepository
				.findSpots(new ParkingModels.ParkingSpotFilter(null, OperationalStatus.AVAILABLE, "test_jet"),
						new RepositoryPageRequest(0, 10, "spotCode", Direction.ASC))
				.items()).contains(spot);

		VisitModels.AircraftVisit visit = this.visitRepository.save(new VisitModels.AircraftVisit(null,
				aircraft.tailNumber(), spot.spotCode(), VisitModels.VisitStatus.ON_RAMP, occurredAt.minusSeconds(600),
				occurredAt, occurredAt.plusSeconds(3600), null, null, null));
		VisitModels.VisitDetail detail = this.visitRepository.findDetail(visit.visitId()).orElseThrow();
		assertThat(detail.manufacturerName()).isEqualTo(modelKey.manufacturerName());
		assertThat(detail.parkingAreaCode()).isEqualTo("TEST_RAMP_EAST");
		assertThat(detail.visit().actualArrivalAt()).isEqualTo(occurredAt);
		assertThat(
				this.visitRepository
						.findOperationalDetails(
								new VisitModels.VisitFilter(List.of(VisitModels.VisitStatus.ON_RAMP), null,
										spot.spotCode(), null, null),
								new RepositoryPageRequest(0, 10, "estimatedArrivalAt", Direction.ASC))
						.items())
				.contains(detail);

		this.serviceRepository
				.saveType(new ServiceModels.ServiceType("test_fuel", "Test Fuel", true, "us_gallon", true, null));
		ServiceModels.ServiceRequest request = this.serviceRepository.saveRequest(new ServiceModels.ServiceRequest(null,
				visit.visitId(), "test_fuel", "test_jet_a", ServiceModels.ServiceRequestStatus.REQUESTED,
				new FixedPrecisionQuantity("125.125"), "us_gallon", null, null, null));
		assertThat(this.serviceRepository.findRequests(
				new ServiceModels.ServiceRequestFilter(visit.visitId(),
						List.of(ServiceModels.ServiceRequestStatus.REQUESTED), "test_fuel"),
				new RepositoryPageRequest(0, 10, "serviceRequestId", Direction.ASC)).items()).containsExactly(request);

		this.fleetRepository
				.saveType(new FleetModels.ServiceVehicleType("test_fuel_truck", "Test Fuel Truck", true, null));
		FleetModels.ServiceVehicle vehicle = this.fleetRepository.saveVehicle(new FleetModels.ServiceVehicle("truck_8",
				"test_fuel_truck", OperationalStatus.AVAILABLE, null, true, null));
		this.fleetRepository.saveFuelTruck(new FleetModels.FuelTruck(vehicle.identifier(), "test_jet_a",
				new FixedPrecisionQuantity("1000.000"), "us_gallon", null));

		this.workforceRepository.saveRole(new WorkforceModels.Role("TEST_OPERATOR", "Repository test role", null));
		WorkforceModels.Worker worker = this.workforceRepository.saveWorker(new WorkforceModels.Worker(null,
				"TEST_OPERATOR", "Ada", "Repository", null, "ada.repository@example.com", true, null));
		WorkforceModels.WorkerShift shift = this.workforceRepository.saveShift(new WorkforceModels.WorkerShift(null,
				worker.workerId(), occurredAt.minusSeconds(3600), occurredAt.plusSeconds(3600),
				occurredAt.minusSeconds(1800), null, WorkforceModels.WorkerShiftStatus.IN_PROGRESS, null, null));
		assertThat(this.workforceRepository.findShifts(worker.workerId(), occurredAt.minusSeconds(7200),
				occurredAt.plusSeconds(7200), new RepositoryPageRequest(0, 10, "scheduledStartAt", Direction.ASC))
				.items()).containsExactly(shift);

		TaskModels.AirportTask task = this.taskRepository.save(new TaskModels.AirportTask(null, visit.visitId(),
				request.serviceRequestId(), vehicle.identifier(), worker.workerId(), "Fuel aircraft", null,
				TaskModels.TaskStatus.IN_PROGRESS, occurredAt.plusSeconds(1800), occurredAt, null, null));
		assertThat(this.taskRepository.findTasks(
				new TaskModels.TaskFilter(visit.visitId(), request.serviceRequestId(), worker.workerId(),
						vehicle.identifier(), List.of(TaskModels.TaskStatus.IN_PROGRESS), null, null),
				new RepositoryPageRequest(0, 10, "dueAt", Direction.ASC)).items()).containsExactly(task);
		VisitModels.OperationalVisitDetail operationalDetail = this.visitRepository
				.findOperationalDetail(visit.visitId()).orElseThrow();
		assertThat(operationalDetail.services()).singleElement()
				.extracting(VisitModels.ServiceSummary::serviceRequestId).isEqualTo(request.serviceRequestId());
		assertThat(operationalDetail.tasks()).singleElement().extracting(VisitModels.TaskSummary::taskId)
				.isEqualTo(task.taskId());
		assertThat(
				this.fleetRepository.findCurrentStatuses(new FleetModels.VehicleFilter(true, null, "test_fuel_truck"),
						new RepositoryPageRequest(0, 10, "identifier", Direction.ASC)).items())
				.singleElement().satisfies(status -> {
					assertThat(status.currentStatus()).isEqualTo(FleetModels.VehicleCurrentState.AT_AIRCRAFT);
					assertThat(status.currentTaskId()).isEqualTo(task.taskId());
				});
		assertThat(this.workforceRepository.findCurrentStatuses(
				new WorkforceModels.WorkerFilter(true, "TEST_OPERATOR",
						List.of(WorkforceModels.WorkerShiftStatus.IN_PROGRESS), "Ada"),
				new RepositoryPageRequest(0, 10, "workerId", Direction.ASC)).items()).singleElement()
				.satisfies(status -> {
					assertThat(status.atWork()).isTrue();
					assertThat(status.currentTaskId()).isEqualTo(task.taskId());
				});

		FuelModels.FuelTank tank = this.fuelRepository.saveTank(new FuelModels.FuelTank("Test Tank 8", "test_jet_a",
				new FixedPrecisionQuantity("5000.000"), "us_gallon", null, true, null));
		FuelModels.FuelLedgerEntry tankEntry = this.fuelRepository
				.append(new FuelModels.FuelLedgerEntry(null, "test_jet_a", tank.name(), null, null, worker.workerId(),
						FuelModels.FuelTransactionType.OPENING_BALANCE, new FixedPrecisionQuantity("1234.567"),
						"us_gallon", null, occurredAt, null, null));
		this.fuelRepository.append(new FuelModels.FuelLedgerEntry(null, "test_jet_a", null, vehicle.identifier(), null,
				worker.workerId(), FuelModels.FuelTransactionType.OPENING_BALANCE,
				new FixedPrecisionQuantity("500.125"), "us_gallon", null, occurredAt, null, null));
		assertThat(tankEntry.occurredAt()).isEqualTo(occurredAt);
		assertThat(this.fuelRepository.findTankBalance(tank.name()).orElseThrow().currentQuantity().value())
				.isEqualTo("1234.567");
		assertThat(this.fuelRepository.findTruckBalance(vehicle.identifier()).orElseThrow().currentQuantity().value())
				.isEqualTo("500.125");
		assertThat(this.fuelRepository.findLedger(new FuelModels.LedgerFilter(tank.name(), null, null, null, null),
				new RepositoryPageRequest(0, 10, "occurredAt", Direction.ASC)).items()).containsExactly(tankEntry);

		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
		transaction.executeWithoutResult(status -> {
			assertThat(this.parkingRepository.lockSpot(spot.spotCode())).isPresent();
			assertThat(this.visitRepository.lock(visit.visitId())).isPresent();
			assertThat(this.serviceRepository.lockRequest(request.serviceRequestId())).isPresent();
			assertThat(this.fleetRepository.lockVehicle(vehicle.identifier())).isPresent();
			assertThat(this.workforceRepository.lockWorker(worker.workerId())).isPresent();
			assertThat(this.taskRepository.lockForAssignment(task.taskId())).isPresent();
			assertThat(this.fuelRepository.lockTankBalance(tank.name())).isPresent();
			assertThat(this.fuelRepository.lockTruckBalance(vehicle.identifier())).isPresent();
		});
	}

	@Test
	void repositoriesTranslateConstraintsAndParticipateInCallerRollback() {
		WorkforceModels.Worker first = this.workforceRepository.saveWorker(new WorkforceModels.Worker(null, "OPERATOR",
				"Constraint", "One", null, "repository.constraint@example.com", true, null));
		assertThat(first.workerId()).isPositive();
		assertThatThrownBy(() -> this.workforceRepository.saveWorker(new WorkforceModels.Worker(null, "OPERATOR",
				"Constraint", "Two", null, "REPOSITORY.CONSTRAINT@EXAMPLE.COM", true, null)))
				.isInstanceOf(PersistenceFailure.class)
				.satisfies(failure -> assertThat(((PersistenceFailure) failure).kind())
						.isEqualTo(PersistenceFailure.Kind.UNIQUE_CONFLICT));

		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
		long rolledBackId = transaction.execute(status -> {
			AdministrationModels.Customer customer = this.administrationRepository.saveCustomer(
					new AdministrationModels.Customer(null, "Repository Rollback", null, null, null, true, null));
			status.setRollbackOnly();
			return customer.customerId();
		});
		assertThat(this.administrationRepository.findCustomer(rolledBackId)).isEmpty();
	}

	@Test
	void applicationServiceCommitsOneIdempotentEffectAndRollsBackAConflictingClaim() {
		this.aircraftRepository
				.saveManufacturer(new AircraftModels.AircraftManufacturer("Workflow Manufacturer", null));
		AircraftModels.AircraftModelKey modelKey = new AircraftModels.AircraftModelKey("Workflow Manufacturer",
				"Workflow Model");
		this.aircraftRepository.saveModel(new AircraftModels.AircraftModel(modelKey, "JET", "WF01", true, null));
		this.aircraftRepository.saveAircraft(new AircraftModels.Aircraft("N700WF", modelKey, "GENERAL_AVIATION",
				"JET_A", null, null, null, true, null));
		ActorContext actor = new ActorContext("integration-dispatcher", java.util.Set.of(Capability.VISITS_WRITE));
		VisitService.CreateVisit command = new VisitService.CreateVisit("N700WF", Instant.parse("2026-08-15T12:00:00Z"),
				Instant.parse("2026-08-15T14:00:00Z"), null);
		IdempotencyKey committedKey = new IdempotencyKey("workflow-create-0001");

		var created = this.visitService.create(command, actor, committedKey);
		var replayed = this.visitService.create(command, actor, committedKey);

		assertThat(created.replayed()).isFalse();
		assertThat(replayed.replayed()).isTrue();
		assertThat(replayed.value().visitId()).isEqualTo(created.value().visitId());
		assertThat(this.jdbcClient.sql("SELECT count(*) FROM aircraft_visits WHERE tail_number = 'N700WF'")
				.query(Long.class).single()).isOne();
		IdempotencyKey conflictingKey = new IdempotencyKey("workflow-create-0002");
		assertThatThrownBy(() -> this.visitService.create(command, actor, conflictingKey))
				.isInstanceOf(DomainConflictException.class);
		assertThat(this.jdbcClient.sql("SELECT count(*) FROM idempotency_records WHERE idempotency_key = :key")
				.param("key", conflictingKey.value()).query(Long.class).single()).isZero();
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
