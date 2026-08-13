# Database schema and migrations

The application-owned Flyway files under [`backend/src/main/resources/db/migration`](../backend/src/main/resources/db/migration) are the only runtime schema and reference-data source. The database design remains documented in [the database design](../docs/database-design.md) and [ER diagram](../docs/database-er-diagram.md).

## Baseline mapping

The obsolete `db/init/001_schema.sql` bootstrap path has been removed. Its exact historical contents are retained only as the committed integration-test fixture [`backend/src/test/resources/db/baseline/001_schema.sql`](../backend/src/test/resources/db/baseline/001_schema.sql). Tests normalize its `psql` transaction wrapper and prove that it maps exactly to `V1__baseline_schema.sql`; the fixture is not packaged and must never be applied as a runtime initializer.

The runtime sequence is:

- `V1__baseline_schema.sql`: the complete historical schema, including PostgreSQL enums, constraints, partial indexes, functions, triggers, sequences, and four current-state/balance views.
- `V2__transactional_idempotency.sql`: atomic command claims/results with retention rules and required uniqueness/indexes.
- `V3__application_privileges.sql`: application grants for the externally provisioned role supplied to Flyway.
- `R__reference_data.sql`: rerunnable generic reference catalogs. Conflict updates occur only when an authoritative seed value changed, so an unchanged rerun does not churn `updated_at`.

Airport settings, parking layout, aircraft/manufacturer/model catalogs, physical vehicles, fuel tanks, workers, and identity links are airport-specific and are never seeded. Authorized administration workflows own them.

## One bootstrap path

Use the [backend local development guide](../backend/README.md#start-locally). From `backend/`, the database bootstrap command is:

```bash
SPRING_PROFILES_ACTIVE=migrate ./mvnw spring-boot:run
```

It uses the same migrations packaged in the application and exits after Flyway succeeds. Do not run the historical fixture, apply ad hoc SQL, or let Hibernate create/update/drop schema.

## Evolution and recovery policy

Versioned migrations are immutable after release. Every correction is a new forward migration. Schema changes follow expand/migrate/contract so the previous and candidate applications can coexist: expand compatibly, migrate/backfill in bounded work, switch behavior, and contract only in a later release.

There are no automatic production down migrations. Application rollback is allowed only while the applied schema remains backward compatible; otherwise use a new forward fix. Restoring a database is reserved for corruption/disaster under the recovery runbook because it can discard committed operational history.

## Useful derived views

- `fuel_tank_balances` returns estimated current fuel-farm inventory.
- `fuel_truck_balances` returns estimated current truck inventory.
- Fuel `current_quantity` may be negative or exceed nominal capacity; `available_capacity` remains the arithmetic difference for reconciliation and may also be outside its nominal range.
- `service_vehicle_current_status` derives whether a vehicle is available, out of service, or at an aircraft.
- `worker_current_status` derives at-work status and the current task.
