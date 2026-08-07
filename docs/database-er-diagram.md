# FBO Manager MVP Entity-Relationship Diagram

This diagram is derived from the [entity-relationship report](database-design.md). It models one FBO at one airport and includes every operational area in the MVP scope. The logical model targets PostgreSQL for both the MVP and Release One; only the database deployment changes between those stages.

```mermaid
erDiagram
    AIRPORT_SETTINGS {
        string icao_code PK
        string name
        string iata_code UK
        string timezone
    }

    CUSTOMERS {
        bigint customer_id PK
        string name
        string phone
        string email
        text notes
        boolean is_active
    }

    FUEL_TYPES {
        string code PK
        string name
        string default_unit
        boolean is_active
    }

    AIRCRAFT_CATEGORIES {
        string code PK
        string name
        text description
    }

    AIRCRAFT_MANUFACTURERS {
        string name PK
    }

    AIRCRAFT_MODELS {
        string manufacturer_name PK, FK
        string model_name PK
        string aircraft_category_code FK
        string icao_type_code
        boolean is_active
    }

    AIRCRAFT {
        string tail_number PK
        string manufacturer_name FK
        string model_name FK
        string fuel_type_code FK
        bigint owner_customer_id FK
        bigint operator_customer_id FK
        text notes
        boolean is_active
    }

    PARKING_AREAS {
        string area_code PK
        string parent_area_code FK
        string name
        text notes
        boolean is_active
    }

    PARKING_SPOTS {
        string spot_code PK
        string parking_area_code FK
        string name
        string operational_status
        text notes
    }

    PARKING_AREA_PREFERENCES {
        string parking_area_code PK, FK
        string aircraft_category_code PK, FK
        int preference_rank
    }

    PARKING_SPOT_PREFERENCES {
        string parking_spot_code PK, FK
        string aircraft_category_code PK, FK
        int preference_rank
    }

    AIRCRAFT_VISITS {
        bigint visit_id PK
        string tail_number FK
        string parking_spot_code FK
        string status
        datetime estimated_arrival_at
        datetime actual_arrival_at
        datetime estimated_departure_at
        datetime actual_departure_at
        text notes
    }

    SERVICE_TYPES {
        string code PK
        string name
        boolean is_fuel_service
        string default_unit
        boolean is_active
    }

    SERVICE_REQUESTS {
        bigint service_request_id PK
        bigint aircraft_visit_id FK
        string service_type_code FK
        string fuel_type_code FK
        string status
        decimal requested_quantity
        string quantity_unit
        text notes
        datetime completed_at
    }

    SERVICE_VEHICLE_TYPES {
        string code PK
        string name
        boolean is_fuel_truck
    }

    SERVICE_VEHICLES {
        string identifier PK
        string service_vehicle_type_code FK
        string operational_status
        text notes
        boolean is_active
    }

    FUEL_TRUCKS {
        string service_vehicle_identifier PK, FK
        string fuel_type_code FK
        decimal capacity
        string quantity_unit
    }

    FUEL_TANKS {
        string name PK
        string fuel_type_code FK
        decimal capacity
        string quantity_unit
        text notes
        boolean is_active
    }

    ROLES {
        string name PK
        text description
    }

    WORKERS {
        bigint worker_id PK
        string role_name FK
        string first_name
        string last_name
        string phone
        string email UK
        boolean is_active
    }

    WORKER_SHIFTS {
        bigint shift_id PK
        bigint worker_id FK
        datetime scheduled_start_at
        datetime scheduled_end_at
        datetime actual_start_at
        datetime actual_end_at
        string status
        text notes
    }

    TASKS {
        bigint task_id PK
        bigint aircraft_visit_id FK
        bigint service_request_id FK
        string service_vehicle_identifier FK
        bigint assigned_worker_id FK
        string title
        text description
        string status
        datetime due_at
        datetime started_at
        datetime completed_at
    }

    FUEL_INVENTORY_TRANSACTIONS {
        bigint fuel_transaction_id PK
        string fuel_type_code FK
        string fuel_tank_name FK
        string fuel_truck_identifier FK
        bigint service_request_id FK
        bigint recorded_by_worker_id FK
        string transaction_type
        decimal quantity_delta
        string quantity_unit
        bigint transfer_group_id
        datetime occurred_at
        text notes
    }

    CUSTOMERS o|--o{ AIRCRAFT : owns
    CUSTOMERS o|--o{ AIRCRAFT : operates
    AIRCRAFT_MANUFACTURERS ||--o{ AIRCRAFT_MODELS : makes
    AIRCRAFT_CATEGORIES ||--o{ AIRCRAFT_MODELS : classifies
    AIRCRAFT_MODELS ||--o{ AIRCRAFT : describes
    FUEL_TYPES ||--o{ AIRCRAFT : requires

    PARKING_AREAS o|--o{ PARKING_AREAS : contains
    PARKING_AREAS ||--o{ PARKING_SPOTS : contains
    PARKING_AREAS ||--o{ PARKING_AREA_PREFERENCES : has
    AIRCRAFT_CATEGORIES ||--o{ PARKING_AREA_PREFERENCES : preferred_for
    PARKING_SPOTS ||--o{ PARKING_SPOT_PREFERENCES : has
    AIRCRAFT_CATEGORIES ||--o{ PARKING_SPOT_PREFERENCES : preferred_for

    AIRCRAFT ||--o{ AIRCRAFT_VISITS : makes
    PARKING_SPOTS o|--o{ AIRCRAFT_VISITS : hosts_over_time
    AIRCRAFT_VISITS ||--o{ SERVICE_REQUESTS : requests
    SERVICE_TYPES ||--o{ SERVICE_REQUESTS : categorizes
    FUEL_TYPES o|--o{ SERVICE_REQUESTS : requested_fuel

    SERVICE_VEHICLE_TYPES ||--o{ SERVICE_VEHICLES : classifies
    SERVICE_VEHICLES ||--o| FUEL_TRUCKS : specializes_as
    FUEL_TYPES ||--o{ FUEL_TRUCKS : carried_by
    FUEL_TYPES ||--o{ FUEL_TANKS : stored_in

    ROLES ||--o{ WORKERS : assigned_to
    WORKERS ||--o{ WORKER_SHIFTS : scheduled_for

    AIRCRAFT_VISITS o|--o{ TASKS : work_for
    SERVICE_REQUESTS o|--o{ TASKS : fulfilled_by
    SERVICE_VEHICLES o|--o{ TASKS : uses
    WORKERS o|--o{ TASKS : assigned

    FUEL_TYPES ||--o{ FUEL_INVENTORY_TRANSACTIONS : identifies
    FUEL_TANKS o|--o{ FUEL_INVENTORY_TRANSACTIONS : ledger_for
    FUEL_TRUCKS o|--o{ FUEL_INVENTORY_TRANSACTIONS : ledger_for
    SERVICE_REQUESTS o|--o{ FUEL_INVENTORY_TRANSACTIONS : caused_by
    WORKERS ||--o{ FUEL_INVENTORY_TRANSACTIONS : records
```

## Reading the diagram

- `||` means exactly one.
- `o|` means zero or one.
- `o{` means zero or many.
- `PK`, `FK`, and `UK` identify primary, foreign, and unique keys.
- String primary keys are natural business keys; composite primary keys use more than one marked attribute.
- `bigint` primary keys are database-generated only where the entity has no safe natural key.
- Nullable foreign keys are represented by `o|` at the parent side of a relationship.
- `AIRPORT_SETTINGS` is intentionally standalone because all operational data implicitly belongs to the single configured airport.

The report contains business constraints that cardinality alone cannot show, including one active visit per aircraft, one on-ramp aircraft per parking spot, one active task per worker or vehicle, and exactly one inventory holder per fuel transaction.
