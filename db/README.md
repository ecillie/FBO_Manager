# Database schema baseline

The current repository contains the initial PostgreSQL schema described in [the database design](../docs/database-design.md) and [ER diagram](../docs/database-er-diagram.md). It creates database-level business constraints plus current-state and fuel-balance views. It is a design/bootstrap artifact, not yet the application-owned migration path.

## Files

- [`init/001_schema.sql`](init/001_schema.sql) creates the schema, constraints, indexes, triggers, sequences, and views.

Reference-data seed and initializer files are not currently present. Backend migration issue [#11](https://github.com/ecillie/FBO_Manager/issues/11) must convert this baseline to ordered Flyway migrations and add idempotent reference seeds before a runnable backend claims database setup support.

## Initialization and migration policy

Issue #10 provides the pinned local PostgreSQL 18 container lifecycle in the [backend local development guide](../backend/README.md). There is intentionally no runnable schema-initialization command until issue #11 provides the Maven/Flyway application context, ordered migration files, and idempotent seed logic. Until then, the local container starts with an empty application database. Do not apply `001_schema.sql` repeatedly to an initialized database or edit a deployed database manually.

The accepted deployment path runs a one-shot migration command from the exact backend image before application replacement. Released migrations are immutable and production fixes move forward with a new migration. See [ADR 0005](../docs/architecture/decisions/0005-portable-single-region-container-deployment.md) and the [deployment architecture](../docs/architecture/deployment.md#7-migration-and-deployment-sequence).

Airport-specific data is never seeded. After backend implementation, an authorized administrator configures the singleton airport and adds its parking layout, aircraft/reference catalogs, fleet, fuel tanks, workers, and identity links through audited application workflows.

## Useful derived views

- `fuel_tank_balances` returns estimated current fuel-farm inventory.
- `fuel_truck_balances` returns estimated current truck inventory.
- Fuel `current_quantity` may be negative or exceed nominal capacity; `available_capacity` remains the arithmetic difference for reconciliation and may also be outside its nominal range.
- `service_vehicle_current_status` derives whether a vehicle is available, out of service, or at an aircraft.
- `worker_current_status` derives at-work status and the current task.
