# Database initialization

The database initializer creates the PostgreSQL schema described in [the ER report](../docs/database-design.md), adds database-level business constraints, creates current-state and fuel-balance views, and seeds reusable reference data.

## Files

- `init/001_schema.sql` creates the schema, constraints, indexes, triggers, sequences, and views.
- `init/002_reference_data.sql` seeds fuel types, aircraft categories, service types, vehicle types, and worker roles.
- `init-db.sh` applies both files in order with `psql` error handling enabled.

Airport-specific data is intentionally not seeded. After initialization, configure the airport and then add its parking layout, aircraft catalog, fleet, fuel tanks, and workers.

## Initialize a new database

Set `DATABASE_URL` to a new, empty PostgreSQL database and run:

```bash
export DATABASE_URL='postgresql://USER:PASSWORD@HOST:5432/fbo_manager'
./db/init-db.sh
```

Then create the required single airport record:

```sql
INSERT INTO airport_settings (icao_code, name, iata_code, timezone)
VALUES ('KXXX', 'Example Airport', NULL, 'America/New_York');
```

Replace every example value before executing the statement. The database permits at most one `airport_settings` row.

## Seed behavior

The reference-data script is safe to rerun. The schema script is an initial migration for a new database and must not be rerun against an initialized database. Future schema changes should be added as ordered migrations rather than editing a deployed database manually.

## Useful derived views

- `fuel_tank_balances` returns current fuel-farm inventory.
- `fuel_truck_balances` returns current truck inventory.
- `service_vehicle_current_status` derives whether a vehicle is available, out of service, or at an aircraft.
- `worker_current_status` derives at-work status and the current task.
