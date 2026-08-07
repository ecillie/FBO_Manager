# FBO Manager MVP Entity-Relationship Report

## 1. Purpose

This report converts the MVP requirements into a logical relational data model for operating one FBO at one airport. It is the source of truth for the entities, attributes, relationships, and business rules shown in the [derived ER diagram](database-er-diagram.md).

The MVP covers:

- aircraft, owners, operators, makes, models, and fuel requirements;
- inbound, on-ramp, departed, and cancelled aircraft visits;
- nested parking areas, parking spots, and aircraft-category preferences;
- requested aircraft services, including fuel quantities and types;
- service vehicles, including specialized fuel-truck data;
- fuel-farm tanks and auditable inventory changes;
- airport tasks, vehicle assignments, and worker assignments; and
- workers, roles, schedules, and attendance.

## 2. Scope and assumptions

### 2.1 Single-airport boundary

The application manages exactly one airport in the MVP. `airport_settings` is a singleton record containing the airport identity and timezone. Operational tables do not carry an `airport_id`; every record belongs to the configured airport.

Multi-airport tenancy, cross-airport reporting, and data sharing are outside the MVP.

### 2.2 Modeling conventions

- A stable, required, domain-meaningful value is used as the primary key when one exists. Codes and operational identifiers used as keys are normalized and treated as immutable.
- Database-generated `BIGINT` identity keys are limited to entities without a safe natural key: customers, aircraft visits, service requests, workers, worker shifts, tasks, and fuel inventory transactions.
- Foreign-key columns use the same name and data type as the natural key they reference. Generated-key foreign keys end in `_id`.
- Mutable entities include `created_at` and `updated_at`; these repeated audit columns are omitted from the entity summaries and diagram for readability.
- Operational timestamps are stored in UTC and displayed in the airport timezone.
- Enumerated statuses are enforced with database checks or database enums.
- Records with operational history are deactivated or archived instead of hard-deleted.
- Quantities use fixed-precision decimals, never floating-point values.

### 2.3 Database platform by delivery stage

PostgreSQL is the database engine for both the MVP and Release One. The deployment becomes more resilient at Release One, but the application does not change database engines or rewrite its schema.

| Stage | Database | Deployment | Required operations |
| --- | --- | --- | --- |
| MVP | PostgreSQL | One PostgreSQL instance and one application database. Developers use the same pinned PostgreSQL major version locally or in a container; the shared MVP environment may use a small managed instance. | Versioned migrations, transactional writes, TLS for hosted connections, automated daily backups, and a documented restore test. |
| Release One | Managed PostgreSQL | A production managed PostgreSQL service using the same major version as the final MVP unless an upgrade is separately tested. | High availability across failure zones where supported, point-in-time recovery, automated backups, encryption at rest and in transit, connection pooling, health alerts, slow-query monitoring, and least-privilege application and migration roles. |

PostgreSQL is selected because the model relies on:

- transactions and row-level locking when dispatchers compete for a parking spot, vehicle, worker, or fuel balance;
- partial unique indexes for rules such as one active aircraft visit and one in-progress assignment;
- composite foreign keys, check constraints, and fixed-precision fuel quantities; and
- recursive queries for nested parking areas.

SQLite is not used as the application database. Its simplicity is useful for isolated prototypes, but changing database engines before Release One would add migration work and expose concurrency differences precisely where this system needs reliable multi-user operational writes.

The Release One change is therefore operational rather than logical: move from a simple PostgreSQL deployment to a managed, monitored, recoverable PostgreSQL service. PostgreSQL documents the relevant [row-level locking](https://www.postgresql.org/docs/current/explicit-locking.html), [partial indexes](https://www.postgresql.org/docs/current/indexes-partial.html), and [point-in-time recovery](https://www.postgresql.org/docs/current/continuous-archiving.html) capabilities.

## 3. Entity catalog

### 3.1 Airport configuration

#### `airport_settings`

Singleton configuration for the airport managed by this deployment.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `icao_code` | PK | Four-character ICAO code and natural airport identifier. |
| `name` | Required | Airport display name. |
| `iata_code` | Optional, unique | Three-character IATA code. |
| `timezone` | Required | IANA timezone used for schedules and display. |

### 3.2 Aircraft reference data and customers

#### `customers`

Stores organizations or individuals that own or operate aircraft.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `customer_id` | PK, generated `BIGINT` | Customer identifier; a generated key is required because names and contact details are neither unique nor immutable. |
| `name` | Required | Organization or individual name. |
| `phone` | Optional | Primary phone number. |
| `email` | Optional | Primary email address. |
| `notes` | Optional | Operational customer notes. |
| `is_active` | Required | Whether the customer is currently active. |

#### `fuel_types`

Reference list for aircraft, fuel requests, fuel trucks, and fuel tanks. Initial values may include `JET_A` and `AVGAS_100LL`.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `code` | PK | Stable fuel code, such as `JET_A` or `AVGAS_100LL`. |
| `name` | Required | Display name. |
| `default_unit` | Required | Default inventory unit, such as `US_GALLON` or `LITER`. |
| `is_active` | Required | Whether the type can be selected for new records. |

#### `aircraft_categories`

Broad aircraft classifications used by aircraft models and parking preferences, such as jet, turboprop, piston, or helicopter.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `code` | PK | Stable category code. |
| `name` | Required | Display name. |
| `description` | Optional | Category guidance. |

#### `aircraft_manufacturers`

| Attribute | Requirement | Description |
| --- | --- | --- |
| `name` | PK | Unique manufacturer name. |

#### `aircraft_models`

Catalog of aircraft makes and models.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `manufacturer_name` | PK/FK | References `aircraft_manufacturers.name`. |
| `model_name` | PK | Model or family name, unique within the manufacturer. |
| `aircraft_category_code` | FK, required | References `aircraft_categories.code`. |
| `icao_type_code` | Optional | Standard ICAO aircraft type code. |
| `is_active` | Required | Whether the model can be selected for new aircraft. |

`manufacturer_name` plus `model_name` is the natural composite primary key.

#### `aircraft`

Represents a physical aircraft independently of any individual visit to the FBO.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `tail_number` | PK | Normalized uppercase registration used as the MVP aircraft identifier. |
| `manufacturer_name` | Composite FK, required | With `model_name`, references `aircraft_models`. |
| `model_name` | Composite FK, required | With `manufacturer_name`, references `aircraft_models`. |
| `fuel_type_code` | FK, required | References `fuel_types.code`. |
| `owner_customer_id` | FK, optional | Current owner; references `customers.customer_id`. |
| `operator_customer_id` | FK, optional | Current operator; references `customers.customer_id`. |
| `notes` | Optional | Aircraft-specific operational notes. |
| `is_active` | Required | Whether the aircraft remains active in the system. |

The current requirements treat a tail number as the aircraft's unique natural key. A future requirement to retain registration history or handle tail-number reuse would require an `aircraft_id` plus a separate aircraft-registration history table.

### 3.3 Parking and aircraft visits

#### `parking_areas`

Hierarchical airport parking areas. A nullable self-reference supports nesting to any required depth.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `area_code` | PK | Stable airport-defined area code. |
| `parent_area_code` | FK, optional | Parent area; references `parking_areas.area_code`. |
| `name` | Required | Area name. |
| `notes` | Optional | Parking guidance or restrictions. |
| `is_active` | Required | Whether the area is available for planning. |

Area codes are unique airport-wide, sibling area names must be unique, and an area cannot be its own ancestor.

#### `parking_spots`

Individual assignable parking positions within an area.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `spot_code` | PK | Stable airport-defined parking-spot code. |
| `parking_area_code` | FK, required | References `parking_areas.area_code`. |
| `name` | Required | Spot name or number. |
| `operational_status` | Required | `AVAILABLE` or `OUT_OF_SERVICE`. Occupancy is derived from visits. |
| `notes` | Optional | Spot-specific guidance. |

`spot_code` is unique airport-wide; `parking_area_code` plus `name` must also be unique.

#### `parking_area_preferences`

Associates an aircraft category with an area where that category should preferably be parked.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `parking_area_code` | PK/FK | References `parking_areas.area_code`. |
| `aircraft_category_code` | PK/FK | References `aircraft_categories.code`. |
| `preference_rank` | Required | Positive number; lower values indicate a stronger preference. |

#### `parking_spot_preferences`

Associates an aircraft category with a preferred individual spot.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `parking_spot_code` | PK/FK | References `parking_spots.spot_code`. |
| `aircraft_category_code` | PK/FK | References `aircraft_categories.code`. |
| `preference_rank` | Required | Positive number; lower values indicate a stronger preference. |

Preferences guide dispatchers but do not prevent a manual parking assignment.

#### `aircraft_visits`

Represents one operational arrival-and-departure cycle. An aircraft may have many visits over time.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `visit_id` | PK, generated `BIGINT` | A generated key is required because expected and actual times can change or collide. |
| `tail_number` | FK, required | References `aircraft.tail_number`. |
| `parking_spot_code` | FK, optional | Current spot; references `parking_spots.spot_code`. |
| `status` | Required | `EXPECTED`, `INBOUND`, `ON_RAMP`, `DEPARTED`, or `CANCELLED`. |
| `estimated_arrival_at` | Optional | Expected arrival time. |
| `actual_arrival_at` | Optional | Actual arrival time. |
| `estimated_departure_at` | Optional | Expected departure time. |
| `actual_departure_at` | Optional | Actual departure time. |
| `notes` | Optional | Visit-specific operational notes. |

Only one active visit (`EXPECTED`, `INBOUND`, or `ON_RAMP`) may exist for an aircraft at a time. Only an `ON_RAMP` visit occupies a parking spot.

### 3.4 Aircraft services

#### `service_types`

Configurable catalog of services. Initial records cover fuel, GPU, lavatory, passenger pickup, passenger drop-off, catering pickup, catering drop-off, and miscellaneous work.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `code` | PK | Stable service code. |
| `name` | Required | Display name. |
| `is_fuel_service` | Required | Identifies services requiring a fuel type and quantity. |
| `default_unit` | Optional | Suggested unit for quantity-based requests. |
| `is_active` | Required | Whether the type can be requested. |

#### `service_requests`

One requested service for an aircraft visit. Multiple services for the same visit are stored as separate rows.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `service_request_id` | PK, generated `BIGINT` | A generated key is required because a visit may contain repeated requests of the same type. |
| `aircraft_visit_id` | FK, required | References `aircraft_visits.visit_id`. |
| `service_type_code` | FK, required | References `service_types.code`. |
| `fuel_type_code` | FK, optional | Required only when the selected type is a fuel service; references `fuel_types.code`. |
| `status` | Required | `REQUESTED`, `IN_PROGRESS`, `COMPLETED`, or `CANCELLED`. |
| `requested_quantity` | Optional | Requested fuel amount or other measurable quantity. |
| `quantity_unit` | Optional | Unit for `requested_quantity`. |
| `notes` | Optional | Instructions or miscellaneous request details. |
| `completed_at` | Optional | Completion time. |

For fuel service requests, `fuel_type_code`, `requested_quantity`, and `quantity_unit` are required and the quantity must be positive.

### 3.5 Service vehicles

#### `service_vehicle_types`

Reference list containing types such as GPU, tug, tow vehicle, lavatory vehicle, fuel truck, and other.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `code` | PK | Stable vehicle-type code. |
| `name` | Required | Display name. |
| `is_fuel_truck` | Required | Whether vehicles of this type require fuel-truck details. |

#### `service_vehicles`

| Attribute | Requirement | Description |
| --- | --- | --- |
| `identifier` | PK | Stable airport fleet number, asset tag, or callsign. |
| `service_vehicle_type_code` | FK, required | References `service_vehicle_types.code`. |
| `operational_status` | Required | `AVAILABLE` or `OUT_OF_SERVICE`; assignment is derived from an active task. |
| `notes` | Optional | Vehicle condition or operational notes. |
| `is_active` | Required | Whether the vehicle remains in the fleet. |

A vehicle is considered at an aircraft when it is attached to an `IN_PROGRESS` task for that aircraft's visit. Otherwise an operational vehicle is available.

#### `fuel_trucks`

One-to-zero-or-one subtype of `service_vehicles` containing fields that apply only to fuel trucks.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `service_vehicle_identifier` | PK/FK | References `service_vehicles.identifier`. |
| `fuel_type_code` | FK, required | References `fuel_types.code`. |
| `capacity` | Required | Maximum truck capacity; must be positive. |
| `quantity_unit` | Required | Unit used for capacity and inventory. |

Current truck quantity is derived from its fuel inventory transactions.

### 3.6 Fuel farm and inventory

#### `fuel_tanks`

| Attribute | Requirement | Description |
| --- | --- | --- |
| `name` | PK | Stable airport-visible tank name or tank number. |
| `fuel_type_code` | FK, required | References `fuel_types.code`. |
| `capacity` | Required | Maximum tank capacity; must be positive. |
| `quantity_unit` | Required | Unit used for capacity and inventory. |
| `notes` | Optional | Tank notes. |
| `is_active` | Required | Whether the tank remains in use. |

Current tank quantity is derived from its fuel inventory transactions.

#### `fuel_inventory_transactions`

Append-only ledger for fuel entering or leaving a tank or fuel truck.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `fuel_transaction_id` | PK, generated `BIGINT` | A generated key is required because transaction contents and timestamps are not guaranteed unique. |
| `fuel_type_code` | FK, required | References `fuel_types.code`. |
| `fuel_tank_name` | FK, optional | References `fuel_tanks.name`. |
| `fuel_truck_identifier` | FK, optional | References `fuel_trucks.service_vehicle_identifier`. |
| `service_request_id` | FK, optional | Fuel service that caused the movement; references `service_requests.service_request_id`. |
| `recorded_by_worker_id` | FK, required | Worker who recorded the movement; references `workers.worker_id`. |
| `transaction_type` | Required | `OPENING_BALANCE`, `RECEIPT`, `TRANSFER`, `DISPENSE`, or `ADJUSTMENT`. |
| `quantity_delta` | Required, nonzero | Signed inventory change: positive adds fuel and negative removes it. |
| `quantity_unit` | Required | Unit for the change. |
| `transfer_group_id` | Optional | Shared value pairing both sides of a tank-to-truck transfer. |
| `occurred_at` | Required | Time of the inventory movement. |
| `notes` | Optional | Explanation, reference, or adjustment reason. |

Exactly one of `fuel_tank_name` and `fuel_truck_identifier` must be present on each transaction. Current inventory is the sum of `quantity_delta` for the tank or truck. Inventory cannot fall below zero or exceed capacity.

### 3.7 Workforce and tasks

#### `roles`

| Attribute | Requirement | Description |
| --- | --- | --- |
| `name` | PK | Stable role name. |
| `description` | Optional | Role responsibilities. |

#### `workers`

| Attribute | Requirement | Description |
| --- | --- | --- |
| `worker_id` | PK, generated `BIGINT` | A generated key is required because names and contact details are neither unique nor immutable. |
| `role_name` | FK, required | References `roles.name`. |
| `first_name` | Required | Given name. |
| `last_name` | Required | Family name. |
| `phone` | Optional | Contact number. |
| `email` | Optional, unique | Contact or login email. |
| `is_active` | Required | Whether the worker remains employed or assignable. |

A worker's current task and at-work status are derived rather than duplicated on this record.

#### `worker_shifts`

Stores scheduled and actual work periods.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `shift_id` | PK, generated `BIGINT` | A generated key is required because a worker may have shifts with matching or revised times. |
| `worker_id` | FK, required | References `workers.worker_id`. |
| `scheduled_start_at` | Required | Scheduled start. |
| `scheduled_end_at` | Required | Scheduled end. |
| `actual_start_at` | Optional | Clock-in time. |
| `actual_end_at` | Optional | Clock-out time. |
| `status` | Required | `SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `ABSENT`, or `CANCELLED`. |
| `notes` | Optional | Shift notes. |

A worker is currently at work when a shift is `IN_PROGRESS`, has an actual start, and has no actual end.

#### `tasks`

Operational work items connecting the airport task, aircraft visit, service request, vehicle, and worker.

| Attribute | Requirement | Description |
| --- | --- | --- |
| `task_id` | PK, generated `BIGINT` | A generated key is required because task content is editable and not guaranteed unique. |
| `aircraft_visit_id` | FK, optional | Aircraft context; references `aircraft_visits.visit_id`. |
| `service_request_id` | FK, optional | Requested service fulfilled by this task; references `service_requests.service_request_id`. |
| `service_vehicle_identifier` | FK, optional | Assigned vehicle; references `service_vehicles.identifier`. |
| `assigned_worker_id` | FK, optional | Assigned worker; references `workers.worker_id`. |
| `title` | Required | Short task name. |
| `description` | Optional | Detailed instructions. |
| `status` | Required | `PENDING`, `IN_PROGRESS`, `COMPLETED`, or `CANCELLED`. |
| `due_at` | Optional | Requested completion time. |
| `started_at` | Optional | Actual start time. |
| `completed_at` | Optional | Actual completion time. |

An airport-wide task may omit the aircraft and service references. When `service_request_id` is present, the task's visit must match the service request's visit. A vehicle or worker may have only one `IN_PROGRESS` task at a time in the MVP.

## 4. Relationship summary

| Parent | Child | Cardinality | Meaning |
| --- | --- | --- | --- |
| `customers` | `aircraft` | 0..1 to many, by role | A customer may own or operate many aircraft; each aircraft has at most one current owner and operator. |
| `aircraft_manufacturers` | `aircraft_models` | one-to-many | A manufacturer defines many models. |
| `aircraft_categories` | `aircraft_models` | one-to-many | Each model belongs to one operational category. |
| `aircraft_models` | `aircraft` | one-to-many | Many physical aircraft can share a model. |
| `fuel_types` | aircraft and fuel entities | one-to-many | One controlled fuel type is reused consistently. |
| `parking_areas` | `parking_areas` | optional-parent hierarchy | An area may contain nested areas. |
| `parking_areas` | `parking_spots` | one-to-many | A spot belongs to exactly one area. |
| parking entities | parking preferences | many-to-many via junctions | Areas and spots may prefer multiple aircraft categories. |
| `aircraft` | `aircraft_visits` | one-to-many | Each visit belongs to one aircraft. |
| `parking_spots` | `aircraft_visits` | optional one-to-many over time | A visit may have one current spot; a spot serves many historical visits. |
| `aircraft_visits` | `service_requests` | one-to-many | A visit may request many services. |
| `service_types` | `service_requests` | one-to-many | Every request has one configured service type. |
| `service_vehicle_types` | `service_vehicles` | one-to-many | Every vehicle has one type. |
| `service_vehicles` | `fuel_trucks` | one-to-zero-or-one | Only fuel vehicles have fuel-truck details. |
| `roles` | `workers` | one-to-many | Every worker has one role. |
| `workers` | `worker_shifts` | one-to-many | A worker has many scheduled shifts. |
| visits, services, vehicles, workers | `tasks` | optional one-to-many | A task links the resources needed to perform work. |
| tanks or fuel trucks | `fuel_inventory_transactions` | one-to-many | Ledger entries produce the current inventory balance. |

## 5. Core business rules and constraints

1. There is exactly one `airport_settings` row, and operational tables have no airport foreign key.
2. String natural keys are normalized before primary-key checks. Tail numbers, codes, and identifiers are trimmed and use a documented case convention.
3. An aircraft has no more than one active visit at a time.
4. A parking spot has no more than one `ON_RAMP` visit at a time.
5. Parking-area parent links must not create a cycle.
6. Parking preferences are advisory; dispatchers may override them.
7. A fuel service requires a fuel type, positive quantity, and unit.
8. A fuel truck's type must be marked as a fuel-truck vehicle type.
9. Fuel inventory transactions are append-only. Corrections use a compensating `ADJUSTMENT` record.
10. A fuel transaction references exactly one inventory holder: a tank or a fuel truck.
11. Fuel type and unit must agree with the referenced tank or truck.
12. Derived inventory must remain between zero and the holder's capacity.
13. Current vehicle location is derived from its `IN_PROGRESS` task and related aircraft visit.
14. Worker at-work status is derived from an open shift; current task is derived from an `IN_PROGRESS` task.
15. A worker and vehicle each have at most one `IN_PROGRESS` task at a time.
16. Historical visits, completed services, completed tasks, shifts, and fuel transactions are retained.
17. Natural keys are treated as immutable after creation. Correcting one requires a controlled transaction that cascades the change to every foreign key.

## 6. State transitions

| Entity | Normal path | Alternate terminal state |
| --- | --- | --- |
| Aircraft visit | `EXPECTED` → `INBOUND` → `ON_RAMP` → `DEPARTED` | `CANCELLED` before departure |
| Service request | `REQUESTED` → `IN_PROGRESS` → `COMPLETED` | `CANCELLED` |
| Task | `PENDING` → `IN_PROGRESS` → `COMPLETED` | `CANCELLED` |
| Worker shift | `SCHEDULED` → `IN_PROGRESS` → `COMPLETED` | `ABSENT` or `CANCELLED` |

## 7. Recommended database enforcement

- Primary-key constraints on every documented natural or generated key, including composite model and preference keys.
- A unique index on a constant expression for `airport_settings` so the natural `icao_code` key cannot be used to insert a second airport row.
- Format checks for ICAO/IATA codes, tail numbers, category and service codes, parking codes, and vehicle identifiers.
- Partial unique index allowing only one active visit per aircraft.
- Partial unique index allowing only one `ON_RAMP` visit per parking spot.
- Partial unique indexes allowing only one `IN_PROGRESS` task per worker and per vehicle.
- Check constraints for positive capacities, valid preference ranks, nonzero ledger deltas, valid time ranges, and exactly one fuel inventory holder.
- Foreign keys default to `RESTRICT` for reference and operational history. Junction records may use `CASCADE` when their parent is deleted before operational use, and natural-key corrections use controlled `ON UPDATE CASCADE` behavior.
- Transactions and row locking around parking assignment, task dispatch, and fuel ledger writes to prevent conflicting concurrent updates.

## 8. Derived diagram

The complete cardinality diagram is maintained in [database-er-diagram.md](database-er-diagram.md). It is derived from the entities and rules in this report; changes to either document should update the other in the same change.
