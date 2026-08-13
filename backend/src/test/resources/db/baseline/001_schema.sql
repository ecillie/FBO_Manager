\set ON_ERROR_STOP on

BEGIN;

CREATE TYPE visit_status AS ENUM (
    'EXPECTED',
    'INBOUND',
    'ON_RAMP',
    'DEPARTED',
    'CANCELLED'
);

CREATE TYPE service_request_status AS ENUM (
    'REQUESTED',
    'IN_PROGRESS',
    'COMPLETED',
    'CANCELLED'
);

CREATE TYPE operational_status AS ENUM (
    'AVAILABLE',
    'OUT_OF_SERVICE'
);

CREATE TYPE worker_shift_status AS ENUM (
    'SCHEDULED',
    'IN_PROGRESS',
    'COMPLETED',
    'ABSENT',
    'CANCELLED'
);

CREATE TYPE task_status AS ENUM (
    'PENDING',
    'IN_PROGRESS',
    'COMPLETED',
    'CANCELLED'
);

CREATE TYPE fuel_transaction_type AS ENUM (
    'OPENING_BALANCE',
    'RECEIPT',
    'TRANSFER',
    'DISPENSE',
    'ADJUSTMENT'
);

CREATE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE TABLE airport_settings (
    icao_code VARCHAR(4) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    iata_code VARCHAR(3) UNIQUE,
    timezone VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT airport_settings_icao_format_ck
        CHECK (icao_code ~ '^[A-Z0-9]{4}$'),
    CONSTRAINT airport_settings_iata_format_ck
        CHECK (iata_code IS NULL OR iata_code ~ '^[A-Z0-9]{3}$'),
    CONSTRAINT airport_settings_name_ck
        CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT airport_settings_timezone_ck
        CHECK (timezone = btrim(timezone) AND timezone <> '')
);

-- A natural airport key is retained while limiting the MVP to one configured airport.
CREATE UNIQUE INDEX airport_settings_singleton_uq
    ON airport_settings ((1));

CREATE TABLE customers (
    customer_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(32),
    email VARCHAR(320),
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT customers_name_ck
        CHECK (name = btrim(name) AND name <> '')
);

CREATE TABLE fuel_types (
    code VARCHAR(32) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    default_unit VARCHAR(32) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fuel_types_code_format_ck
        CHECK (code ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    CONSTRAINT fuel_types_name_ck
        CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT fuel_types_unit_ck
        CHECK (default_unit ~ '^[A-Z][A-Z0-9_]{0,31}$')
);

CREATE TABLE aircraft_categories (
    code VARCHAR(32) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT aircraft_categories_code_format_ck
        CHECK (code ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    CONSTRAINT aircraft_categories_name_ck
        CHECK (name = btrim(name) AND name <> '')
);

CREATE TABLE aircraft_operation_types (
    code VARCHAR(32) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT aircraft_operation_types_code_format_ck
        CHECK (code ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    CONSTRAINT aircraft_operation_types_name_ck
        CHECK (name = btrim(name) AND name <> '')
);

CREATE UNIQUE INDEX aircraft_operation_types_name_ci_uq
    ON aircraft_operation_types (lower(name));

CREATE TABLE aircraft_manufacturers (
    name VARCHAR(120) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT aircraft_manufacturers_name_ck
        CHECK (name = btrim(name) AND name <> '')
);

CREATE UNIQUE INDEX aircraft_manufacturers_name_ci_uq
    ON aircraft_manufacturers (lower(name));

CREATE TABLE aircraft_models (
    manufacturer_name VARCHAR(120) NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    aircraft_category_code VARCHAR(32) NOT NULL,
    icao_type_code VARCHAR(4),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (manufacturer_name, model_name),
    CONSTRAINT aircraft_models_manufacturer_fk
        FOREIGN KEY (manufacturer_name)
        REFERENCES aircraft_manufacturers (name)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT aircraft_models_category_fk
        FOREIGN KEY (aircraft_category_code)
        REFERENCES aircraft_categories (code)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT aircraft_models_model_name_ck
        CHECK (model_name = btrim(model_name) AND model_name <> ''),
    CONSTRAINT aircraft_models_icao_type_format_ck
        CHECK (icao_type_code IS NULL OR icao_type_code ~ '^[A-Z0-9]{2,4}$')
);

CREATE TABLE aircraft (
    tail_number VARCHAR(12) PRIMARY KEY,
    manufacturer_name VARCHAR(120) NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    aircraft_operation_type_code VARCHAR(32) NOT NULL,
    fuel_type_code VARCHAR(32) NOT NULL,
    owner_customer_id BIGINT,
    operator_customer_id BIGINT,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT aircraft_model_fk
        FOREIGN KEY (manufacturer_name, model_name)
        REFERENCES aircraft_models (manufacturer_name, model_name)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT aircraft_operation_type_fk
        FOREIGN KEY (aircraft_operation_type_code)
        REFERENCES aircraft_operation_types (code)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT aircraft_fuel_type_fk
        FOREIGN KEY (fuel_type_code)
        REFERENCES fuel_types (code)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT aircraft_owner_fk
        FOREIGN KEY (owner_customer_id)
        REFERENCES customers (customer_id)
        ON DELETE RESTRICT,
    CONSTRAINT aircraft_operator_fk
        FOREIGN KEY (operator_customer_id)
        REFERENCES customers (customer_id)
        ON DELETE RESTRICT,
    CONSTRAINT aircraft_tail_number_format_ck
        CHECK (tail_number ~ '^[A-Z0-9][A-Z0-9-]{1,11}$')
);

CREATE INDEX aircraft_operation_type_idx
    ON aircraft (aircraft_operation_type_code);

CREATE TABLE parking_areas (
    area_code VARCHAR(32) PRIMARY KEY,
    parent_area_code VARCHAR(32),
    name VARCHAR(120) NOT NULL,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT parking_areas_parent_fk
        FOREIGN KEY (parent_area_code)
        REFERENCES parking_areas (area_code)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT parking_areas_code_format_ck
        CHECK (area_code ~ '^[A-Z0-9][A-Z0-9_-]{0,31}$'),
    CONSTRAINT parking_areas_not_own_parent_ck
        CHECK (parent_area_code IS NULL OR parent_area_code <> area_code),
    CONSTRAINT parking_areas_name_ck
        CHECK (name = btrim(name) AND name <> '')
);

CREATE UNIQUE INDEX parking_areas_sibling_name_ci_uq
    ON parking_areas (COALESCE(parent_area_code, ''), lower(name));

CREATE TABLE parking_spots (
    spot_code VARCHAR(32) PRIMARY KEY,
    parking_area_code VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    operational_status operational_status NOT NULL DEFAULT 'AVAILABLE',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT parking_spots_area_fk
        FOREIGN KEY (parking_area_code)
        REFERENCES parking_areas (area_code)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT parking_spots_code_format_ck
        CHECK (spot_code ~ '^[A-Z0-9][A-Z0-9_-]{0,31}$'),
    CONSTRAINT parking_spots_name_ck
        CHECK (name = btrim(name) AND name <> '')
);

CREATE UNIQUE INDEX parking_spots_area_name_ci_uq
    ON parking_spots (parking_area_code, lower(name));

CREATE TABLE parking_area_preferences (
    parking_area_code VARCHAR(32) NOT NULL,
    aircraft_category_code VARCHAR(32) NOT NULL,
    preference_rank SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (parking_area_code, aircraft_category_code),
    CONSTRAINT parking_area_preferences_area_fk
        FOREIGN KEY (parking_area_code)
        REFERENCES parking_areas (area_code)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT parking_area_preferences_category_fk
        FOREIGN KEY (aircraft_category_code)
        REFERENCES aircraft_categories (code)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT parking_area_preferences_rank_ck
        CHECK (preference_rank > 0)
);

CREATE TABLE parking_spot_preferences (
    parking_spot_code VARCHAR(32) NOT NULL,
    aircraft_category_code VARCHAR(32) NOT NULL,
    preference_rank SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (parking_spot_code, aircraft_category_code),
    CONSTRAINT parking_spot_preferences_spot_fk
        FOREIGN KEY (parking_spot_code)
        REFERENCES parking_spots (spot_code)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT parking_spot_preferences_category_fk
        FOREIGN KEY (aircraft_category_code)
        REFERENCES aircraft_categories (code)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT parking_spot_preferences_rank_ck
        CHECK (preference_rank > 0)
);

CREATE TABLE aircraft_visits (
    visit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tail_number VARCHAR(12) NOT NULL,
    parking_spot_code VARCHAR(32),
    status visit_status NOT NULL DEFAULT 'EXPECTED',
    estimated_arrival_at TIMESTAMPTZ,
    actual_arrival_at TIMESTAMPTZ,
    estimated_departure_at TIMESTAMPTZ,
    actual_departure_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT aircraft_visits_aircraft_fk
        FOREIGN KEY (tail_number)
        REFERENCES aircraft (tail_number)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT aircraft_visits_parking_spot_fk
        FOREIGN KEY (parking_spot_code)
        REFERENCES parking_spots (spot_code)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT aircraft_visits_estimated_time_order_ck
        CHECK (
            estimated_departure_at IS NULL
            OR estimated_arrival_at IS NULL
            OR estimated_departure_at >= estimated_arrival_at
        ),
    CONSTRAINT aircraft_visits_actual_time_order_ck
        CHECK (
            actual_departure_at IS NULL
            OR actual_arrival_at IS NULL
            OR actual_departure_at >= actual_arrival_at
        ),
    CONSTRAINT aircraft_visits_on_ramp_fields_ck
        CHECK (
            status <> 'ON_RAMP'
            OR (parking_spot_code IS NOT NULL AND actual_arrival_at IS NOT NULL)
        ),
    CONSTRAINT aircraft_visits_departed_fields_ck
        CHECK (
            status <> 'DEPARTED'
            OR (actual_arrival_at IS NOT NULL AND actual_departure_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX aircraft_visits_one_active_per_aircraft_uq
    ON aircraft_visits (tail_number)
    WHERE status IN ('EXPECTED', 'INBOUND', 'ON_RAMP');

CREATE UNIQUE INDEX aircraft_visits_one_on_ramp_per_spot_uq
    ON aircraft_visits (parking_spot_code)
    WHERE status = 'ON_RAMP' AND parking_spot_code IS NOT NULL;

CREATE INDEX aircraft_visits_status_arrival_idx
    ON aircraft_visits (status, estimated_arrival_at);

CREATE TABLE service_types (
    code VARCHAR(32) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    is_fuel_service BOOLEAN NOT NULL DEFAULT FALSE,
    default_unit VARCHAR(32),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT service_types_code_format_ck
        CHECK (code ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    CONSTRAINT service_types_name_ck
        CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT service_types_unit_ck
        CHECK (default_unit IS NULL OR default_unit ~ '^[A-Z][A-Z0-9_]{0,31}$')
);

CREATE TABLE service_requests (
    service_request_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aircraft_visit_id BIGINT NOT NULL,
    service_type_code VARCHAR(32) NOT NULL,
    fuel_type_code VARCHAR(32),
    status service_request_status NOT NULL DEFAULT 'REQUESTED',
    requested_quantity NUMERIC(14, 3),
    quantity_unit VARCHAR(32),
    notes TEXT,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT service_requests_visit_fk
        FOREIGN KEY (aircraft_visit_id)
        REFERENCES aircraft_visits (visit_id)
        ON DELETE RESTRICT,
    CONSTRAINT service_requests_service_type_fk
        FOREIGN KEY (service_type_code)
        REFERENCES service_types (code)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT service_requests_fuel_type_fk
        FOREIGN KEY (fuel_type_code)
        REFERENCES fuel_types (code)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT service_requests_quantity_ck
        CHECK (requested_quantity IS NULL OR requested_quantity > 0),
    CONSTRAINT service_requests_unit_ck
        CHECK (quantity_unit IS NULL OR quantity_unit ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    CONSTRAINT service_requests_completed_at_ck
        CHECK (
            (status = 'COMPLETED' AND completed_at IS NOT NULL)
            OR (status <> 'COMPLETED' AND completed_at IS NULL)
        )
);

CREATE INDEX service_requests_visit_status_idx
    ON service_requests (aircraft_visit_id, status);

CREATE TABLE service_vehicle_types (
    code VARCHAR(32) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    is_fuel_truck BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT service_vehicle_types_code_format_ck
        CHECK (code ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    CONSTRAINT service_vehicle_types_name_ck
        CHECK (name = btrim(name) AND name <> '')
);

CREATE TABLE service_vehicles (
    identifier VARCHAR(32) PRIMARY KEY,
    service_vehicle_type_code VARCHAR(32) NOT NULL,
    operational_status operational_status NOT NULL DEFAULT 'AVAILABLE',
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT service_vehicles_type_fk
        FOREIGN KEY (service_vehicle_type_code)
        REFERENCES service_vehicle_types (code)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT service_vehicles_identifier_format_ck
        CHECK (identifier ~ '^[A-Z0-9][A-Z0-9_-]{0,31}$')
);

CREATE TABLE fuel_trucks (
    service_vehicle_identifier VARCHAR(32) PRIMARY KEY,
    fuel_type_code VARCHAR(32) NOT NULL,
    capacity NUMERIC(14, 3) NOT NULL,
    quantity_unit VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fuel_trucks_vehicle_fk
        FOREIGN KEY (service_vehicle_identifier)
        REFERENCES service_vehicles (identifier)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fuel_trucks_fuel_type_fk
        FOREIGN KEY (fuel_type_code)
        REFERENCES fuel_types (code)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fuel_trucks_capacity_ck
        CHECK (capacity > 0),
    CONSTRAINT fuel_trucks_unit_ck
        CHECK (quantity_unit ~ '^[A-Z][A-Z0-9_]{0,31}$')
);

CREATE TABLE fuel_tanks (
    name VARCHAR(120) PRIMARY KEY,
    fuel_type_code VARCHAR(32) NOT NULL,
    capacity NUMERIC(14, 3) NOT NULL,
    quantity_unit VARCHAR(32) NOT NULL,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fuel_tanks_fuel_type_fk
        FOREIGN KEY (fuel_type_code)
        REFERENCES fuel_types (code)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fuel_tanks_name_ck
        CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT fuel_tanks_capacity_ck
        CHECK (capacity > 0),
    CONSTRAINT fuel_tanks_unit_ck
        CHECK (quantity_unit ~ '^[A-Z][A-Z0-9_]{0,31}$')
);

CREATE UNIQUE INDEX fuel_tanks_name_ci_uq
    ON fuel_tanks (lower(name));

CREATE TABLE roles (
    name VARCHAR(120) PRIMARY KEY,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT roles_name_ck
        CHECK (name = btrim(name) AND name <> '')
);

CREATE UNIQUE INDEX roles_name_ci_uq
    ON roles (lower(name));

CREATE TABLE workers (
    worker_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_name VARCHAR(120) NOT NULL,
    first_name VARCHAR(120) NOT NULL,
    last_name VARCHAR(120) NOT NULL,
    phone VARCHAR(32),
    email VARCHAR(320),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT workers_role_fk
        FOREIGN KEY (role_name)
        REFERENCES roles (name)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT workers_first_name_ck
        CHECK (first_name = btrim(first_name) AND first_name <> ''),
    CONSTRAINT workers_last_name_ck
        CHECK (last_name = btrim(last_name) AND last_name <> '')
);

CREATE UNIQUE INDEX workers_email_ci_uq
    ON workers (lower(email))
    WHERE email IS NOT NULL;

CREATE TABLE worker_shifts (
    shift_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    worker_id BIGINT NOT NULL,
    scheduled_start_at TIMESTAMPTZ NOT NULL,
    scheduled_end_at TIMESTAMPTZ NOT NULL,
    actual_start_at TIMESTAMPTZ,
    actual_end_at TIMESTAMPTZ,
    status worker_shift_status NOT NULL DEFAULT 'SCHEDULED',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT worker_shifts_worker_fk
        FOREIGN KEY (worker_id)
        REFERENCES workers (worker_id)
        ON DELETE RESTRICT,
    CONSTRAINT worker_shifts_scheduled_time_order_ck
        CHECK (scheduled_end_at > scheduled_start_at),
    CONSTRAINT worker_shifts_actual_time_order_ck
        CHECK (
            actual_end_at IS NULL
            OR (actual_start_at IS NOT NULL AND actual_end_at >= actual_start_at)
        ),
    CONSTRAINT worker_shifts_in_progress_fields_ck
        CHECK (
            status <> 'IN_PROGRESS'
            OR (actual_start_at IS NOT NULL AND actual_end_at IS NULL)
        ),
    CONSTRAINT worker_shifts_completed_fields_ck
        CHECK (
            status <> 'COMPLETED'
            OR (actual_start_at IS NOT NULL AND actual_end_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX worker_shifts_one_in_progress_per_worker_uq
    ON worker_shifts (worker_id)
    WHERE status = 'IN_PROGRESS';

CREATE INDEX worker_shifts_worker_schedule_idx
    ON worker_shifts (worker_id, scheduled_start_at);

CREATE TABLE tasks (
    task_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aircraft_visit_id BIGINT,
    service_request_id BIGINT,
    service_vehicle_identifier VARCHAR(32),
    assigned_worker_id BIGINT,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status task_status NOT NULL DEFAULT 'PENDING',
    due_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tasks_visit_fk
        FOREIGN KEY (aircraft_visit_id)
        REFERENCES aircraft_visits (visit_id)
        ON DELETE RESTRICT,
    CONSTRAINT tasks_service_request_fk
        FOREIGN KEY (service_request_id)
        REFERENCES service_requests (service_request_id)
        ON DELETE RESTRICT,
    CONSTRAINT tasks_vehicle_fk
        FOREIGN KEY (service_vehicle_identifier)
        REFERENCES service_vehicles (identifier)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT tasks_worker_fk
        FOREIGN KEY (assigned_worker_id)
        REFERENCES workers (worker_id)
        ON DELETE RESTRICT,
    CONSTRAINT tasks_title_ck
        CHECK (title = btrim(title) AND title <> ''),
    CONSTRAINT tasks_in_progress_fields_ck
        CHECK (
            status <> 'IN_PROGRESS'
            OR (assigned_worker_id IS NOT NULL AND started_at IS NOT NULL)
        ),
    CONSTRAINT tasks_completed_fields_ck
        CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL),
    CONSTRAINT tasks_time_order_ck
        CHECK (
            completed_at IS NULL
            OR started_at IS NULL
            OR completed_at >= started_at
        )
);

CREATE UNIQUE INDEX tasks_one_in_progress_per_worker_uq
    ON tasks (assigned_worker_id)
    WHERE status = 'IN_PROGRESS' AND assigned_worker_id IS NOT NULL;

CREATE UNIQUE INDEX tasks_one_in_progress_per_vehicle_uq
    ON tasks (service_vehicle_identifier)
    WHERE status = 'IN_PROGRESS' AND service_vehicle_identifier IS NOT NULL;

CREATE INDEX tasks_visit_status_idx
    ON tasks (aircraft_visit_id, status);

CREATE SEQUENCE fuel_transfer_group_id_seq AS BIGINT;

CREATE TABLE fuel_inventory_transactions (
    fuel_transaction_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fuel_type_code VARCHAR(32) NOT NULL,
    fuel_tank_name VARCHAR(120),
    fuel_truck_identifier VARCHAR(32),
    service_request_id BIGINT,
    recorded_by_worker_id BIGINT NOT NULL,
    transaction_type fuel_transaction_type NOT NULL,
    quantity_delta NUMERIC(14, 3) NOT NULL,
    quantity_unit VARCHAR(32) NOT NULL,
    transfer_group_id BIGINT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fuel_inventory_transactions_fuel_type_fk
        FOREIGN KEY (fuel_type_code)
        REFERENCES fuel_types (code)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fuel_inventory_transactions_tank_fk
        FOREIGN KEY (fuel_tank_name)
        REFERENCES fuel_tanks (name)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fuel_inventory_transactions_truck_fk
        FOREIGN KEY (fuel_truck_identifier)
        REFERENCES fuel_trucks (service_vehicle_identifier)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fuel_inventory_transactions_service_request_fk
        FOREIGN KEY (service_request_id)
        REFERENCES service_requests (service_request_id)
        ON DELETE RESTRICT,
    CONSTRAINT fuel_inventory_transactions_worker_fk
        FOREIGN KEY (recorded_by_worker_id)
        REFERENCES workers (worker_id)
        ON DELETE RESTRICT,
    CONSTRAINT fuel_inventory_transactions_one_holder_ck
        CHECK (num_nonnulls(fuel_tank_name, fuel_truck_identifier) = 1),
    CONSTRAINT fuel_inventory_transactions_quantity_ck
        CHECK (quantity_delta <> 0),
    CONSTRAINT fuel_inventory_transactions_unit_ck
        CHECK (quantity_unit ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    CONSTRAINT fuel_inventory_transactions_positive_addition_ck
        CHECK (
            transaction_type NOT IN ('OPENING_BALANCE', 'RECEIPT')
            OR quantity_delta > 0
        ),
    CONSTRAINT fuel_inventory_transactions_negative_dispense_ck
        CHECK (transaction_type <> 'DISPENSE' OR quantity_delta < 0),
    CONSTRAINT fuel_inventory_transactions_transfer_group_ck
        CHECK (transaction_type <> 'TRANSFER' OR transfer_group_id IS NOT NULL),
    CONSTRAINT fuel_inventory_transactions_dispense_request_ck
        CHECK (transaction_type <> 'DISPENSE' OR service_request_id IS NOT NULL),
    CONSTRAINT fuel_inventory_transactions_adjustment_notes_ck
        CHECK (
            transaction_type <> 'ADJUSTMENT'
            OR (notes IS NOT NULL AND btrim(notes) <> '')
        )
);

CREATE INDEX fuel_inventory_transactions_tank_time_idx
    ON fuel_inventory_transactions (fuel_tank_name, occurred_at)
    WHERE fuel_tank_name IS NOT NULL;

CREATE INDEX fuel_inventory_transactions_truck_time_idx
    ON fuel_inventory_transactions (fuel_truck_identifier, occurred_at)
    WHERE fuel_truck_identifier IS NOT NULL;

CREATE INDEX fuel_inventory_transactions_service_request_idx
    ON fuel_inventory_transactions (service_request_id)
    WHERE service_request_id IS NOT NULL;

CREATE FUNCTION prevent_parking_area_cycle()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.parent_area_code IS NULL THEN
        RETURN NEW;
    END IF;

    IF NEW.parent_area_code = NEW.area_code THEN
        RAISE EXCEPTION 'Parking area % cannot be its own parent', NEW.area_code;
    END IF;

    IF EXISTS (
        WITH RECURSIVE ancestors AS (
            SELECT area_code, parent_area_code
            FROM parking_areas
            WHERE area_code = NEW.parent_area_code

            UNION ALL

            SELECT parent.area_code, parent.parent_area_code
            FROM parking_areas AS parent
            JOIN ancestors AS child
              ON parent.area_code = child.parent_area_code
        )
        SELECT 1
        FROM ancestors
        WHERE area_code = NEW.area_code
    ) THEN
        RAISE EXCEPTION 'Parking area hierarchy cannot contain a cycle';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER parking_areas_prevent_cycle
BEFORE INSERT OR UPDATE OF area_code, parent_area_code
ON parking_areas
FOR EACH ROW
EXECUTE FUNCTION prevent_parking_area_cycle();

CREATE FUNCTION validate_visit_parking()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    spot_status operational_status;
BEGIN
    IF NEW.status <> 'ON_RAMP' THEN
        RETURN NEW;
    END IF;

    SELECT operational_status
      INTO spot_status
      FROM parking_spots
     WHERE spot_code = NEW.parking_spot_code
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Parking spot % does not exist', NEW.parking_spot_code;
    END IF;

    IF spot_status <> 'AVAILABLE' THEN
        RAISE EXCEPTION 'Parking spot % is out of service', NEW.parking_spot_code;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER aircraft_visits_validate_parking
BEFORE INSERT OR UPDATE OF status, parking_spot_code
ON aircraft_visits
FOR EACH ROW
EXECUTE FUNCTION validate_visit_parking();

CREATE FUNCTION prevent_occupied_spot_shutdown()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.operational_status = 'OUT_OF_SERVICE'
       AND OLD.operational_status IS DISTINCT FROM NEW.operational_status
       AND EXISTS (
           SELECT 1
           FROM aircraft_visits
           WHERE parking_spot_code = NEW.spot_code
             AND status = 'ON_RAMP'
       ) THEN
        RAISE EXCEPTION 'Parking spot % is occupied', NEW.spot_code;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER parking_spots_prevent_occupied_shutdown
BEFORE UPDATE OF operational_status
ON parking_spots
FOR EACH ROW
EXECUTE FUNCTION prevent_occupied_spot_shutdown();

CREATE FUNCTION validate_service_request_fuel_fields()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    fuel_service BOOLEAN;
BEGIN
    SELECT is_fuel_service
      INTO fuel_service
      FROM service_types
     WHERE code = NEW.service_type_code;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Service type % does not exist', NEW.service_type_code;
    END IF;

    IF fuel_service THEN
        IF NEW.fuel_type_code IS NULL
           OR NEW.requested_quantity IS NULL
           OR NEW.quantity_unit IS NULL THEN
            RAISE EXCEPTION 'Fuel requests require fuel type, quantity, and unit';
        END IF;
    ELSIF NEW.fuel_type_code IS NOT NULL THEN
        RAISE EXCEPTION 'Only fuel services may specify a fuel type';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER service_requests_validate_fuel_fields
BEFORE INSERT OR UPDATE OF service_type_code, fuel_type_code, requested_quantity, quantity_unit
ON service_requests
FOR EACH ROW
EXECUTE FUNCTION validate_service_request_fuel_fields();

CREATE FUNCTION protect_service_type_fuel_flag()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.is_fuel_service IS DISTINCT FROM NEW.is_fuel_service
       AND EXISTS (
           SELECT 1
           FROM service_requests
           WHERE service_type_code = OLD.code
       ) THEN
        RAISE EXCEPTION 'Cannot change fuel classification after requests exist';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER service_types_protect_fuel_flag
BEFORE UPDATE OF is_fuel_service
ON service_types
FOR EACH ROW
EXECUTE FUNCTION protect_service_type_fuel_flag();

CREATE FUNCTION validate_fuel_truck_type()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    valid_fuel_truck BOOLEAN;
BEGIN
    SELECT vehicle_type.is_fuel_truck
      INTO valid_fuel_truck
      FROM service_vehicles AS vehicle
      JOIN service_vehicle_types AS vehicle_type
        ON vehicle_type.code = vehicle.service_vehicle_type_code
     WHERE vehicle.identifier = NEW.service_vehicle_identifier;

    IF NOT FOUND OR NOT valid_fuel_truck THEN
        RAISE EXCEPTION 'Vehicle % is not configured as a fuel truck',
            NEW.service_vehicle_identifier;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER fuel_trucks_validate_vehicle_type
BEFORE INSERT OR UPDATE OF service_vehicle_identifier
ON fuel_trucks
FOR EACH ROW
EXECUTE FUNCTION validate_fuel_truck_type();

CREATE FUNCTION prevent_fuel_vehicle_reclassification()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    new_type_is_fuel_truck BOOLEAN;
BEGIN
    IF OLD.service_vehicle_type_code = NEW.service_vehicle_type_code THEN
        RETURN NEW;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM fuel_trucks
        WHERE service_vehicle_identifier = OLD.identifier
    ) THEN
        SELECT is_fuel_truck
          INTO new_type_is_fuel_truck
          FROM service_vehicle_types
         WHERE code = NEW.service_vehicle_type_code;

        IF NOT COALESCE(new_type_is_fuel_truck, FALSE) THEN
            RAISE EXCEPTION 'Fuel truck % must retain a fuel-truck vehicle type',
                OLD.identifier;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER service_vehicles_prevent_fuel_reclassification
BEFORE UPDATE OF service_vehicle_type_code
ON service_vehicles
FOR EACH ROW
EXECUTE FUNCTION prevent_fuel_vehicle_reclassification();

CREATE FUNCTION validate_task_context()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    request_visit_id BIGINT;
    vehicle_status operational_status;
BEGIN
    IF NEW.service_request_id IS NOT NULL THEN
        SELECT aircraft_visit_id
          INTO request_visit_id
          FROM service_requests
         WHERE service_request_id = NEW.service_request_id;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Service request % does not exist', NEW.service_request_id;
        END IF;

        IF NEW.aircraft_visit_id IS NULL THEN
            NEW.aircraft_visit_id = request_visit_id;
        ELSIF NEW.aircraft_visit_id <> request_visit_id THEN
            RAISE EXCEPTION 'Task visit must match its service request visit';
        END IF;
    END IF;

    IF NEW.status = 'IN_PROGRESS'
       AND NEW.service_vehicle_identifier IS NOT NULL THEN
        SELECT operational_status
          INTO vehicle_status
          FROM service_vehicles
         WHERE identifier = NEW.service_vehicle_identifier
         FOR UPDATE;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Service vehicle % does not exist',
                NEW.service_vehicle_identifier;
        END IF;

        IF vehicle_status <> 'AVAILABLE' THEN
            RAISE EXCEPTION 'Service vehicle % is out of service',
                NEW.service_vehicle_identifier;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tasks_validate_context
BEFORE INSERT OR UPDATE OF aircraft_visit_id, service_request_id,
    service_vehicle_identifier, status
ON tasks
FOR EACH ROW
EXECUTE FUNCTION validate_task_context();

CREATE FUNCTION validate_fuel_inventory_transaction()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    holder_fuel_type VARCHAR(32);
    holder_unit VARCHAR(32);
    request_fuel_type VARCHAR(32);
    request_is_fuel_service BOOLEAN;
BEGIN
    IF NEW.fuel_tank_name IS NOT NULL THEN
        SELECT fuel_type_code, quantity_unit
          INTO holder_fuel_type, holder_unit
          FROM fuel_tanks
         WHERE name = NEW.fuel_tank_name
         FOR UPDATE;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Fuel tank % does not exist', NEW.fuel_tank_name;
        END IF;
    ELSE
        SELECT fuel_type_code, quantity_unit
          INTO holder_fuel_type, holder_unit
          FROM fuel_trucks
         WHERE service_vehicle_identifier = NEW.fuel_truck_identifier
         FOR UPDATE;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Fuel truck % does not exist', NEW.fuel_truck_identifier;
        END IF;
    END IF;

    IF NEW.fuel_type_code <> holder_fuel_type THEN
        RAISE EXCEPTION 'Transaction fuel type must match its tank or truck';
    END IF;

    IF NEW.quantity_unit <> holder_unit THEN
        RAISE EXCEPTION 'Transaction unit must match its tank or truck';
    END IF;

    -- Ledger balances are operational estimates. Negative and above-capacity
    -- totals remain visible for reconciliation and are not rejected here.
    IF NEW.service_request_id IS NOT NULL THEN
        SELECT request.fuel_type_code, service_type.is_fuel_service
          INTO request_fuel_type, request_is_fuel_service
          FROM service_requests AS request
          JOIN service_types AS service_type
            ON service_type.code = request.service_type_code
         WHERE request.service_request_id = NEW.service_request_id;

        IF NOT FOUND OR NOT request_is_fuel_service THEN
            RAISE EXCEPTION 'Inventory transaction must reference a fuel service request';
        END IF;

        IF request_fuel_type <> NEW.fuel_type_code THEN
            RAISE EXCEPTION 'Transaction fuel type must match its service request';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER fuel_inventory_transactions_validate
BEFORE INSERT
ON fuel_inventory_transactions
FOR EACH ROW
EXECUTE FUNCTION validate_fuel_inventory_transaction();

CREATE FUNCTION prevent_fuel_inventory_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    -- Permit only internal foreign-key cascades caused by a controlled key correction.
    IF TG_OP = 'UPDATE' AND pg_trigger_depth() > 1 THEN
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'Fuel inventory transactions are append-only; add an adjustment instead';
END;
$$;

CREATE TRIGGER fuel_inventory_transactions_append_only
BEFORE UPDATE OR DELETE
ON fuel_inventory_transactions
FOR EACH ROW
EXECUTE FUNCTION prevent_fuel_inventory_mutation();

DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'airport_settings',
        'customers',
        'fuel_types',
        'aircraft_categories',
        'aircraft_operation_types',
        'aircraft_manufacturers',
        'aircraft_models',
        'aircraft',
        'parking_areas',
        'parking_spots',
        'parking_area_preferences',
        'parking_spot_preferences',
        'aircraft_visits',
        'service_types',
        'service_requests',
        'service_vehicle_types',
        'service_vehicles',
        'fuel_trucks',
        'fuel_tanks',
        'roles',
        'workers',
        'worker_shifts',
        'tasks'
    ]
    LOOP
        EXECUTE format(
            'CREATE TRIGGER %I BEFORE UPDATE ON %I '
            'FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
            table_name || '_set_updated_at',
            table_name
        );
    END LOOP;
END;
$$;

CREATE VIEW fuel_tank_balances AS
SELECT
    tank.name,
    tank.fuel_type_code,
    tank.capacity,
    tank.quantity_unit,
    COALESCE(SUM(transaction.quantity_delta), 0)::NUMERIC(14, 3) AS current_quantity,
    (
        tank.capacity - COALESCE(SUM(transaction.quantity_delta), 0)
    )::NUMERIC(14, 3) AS available_capacity
FROM fuel_tanks AS tank
LEFT JOIN fuel_inventory_transactions AS transaction
  ON transaction.fuel_tank_name = tank.name
GROUP BY tank.name, tank.fuel_type_code, tank.capacity, tank.quantity_unit;

CREATE VIEW fuel_truck_balances AS
SELECT
    truck.service_vehicle_identifier,
    truck.fuel_type_code,
    truck.capacity,
    truck.quantity_unit,
    COALESCE(SUM(transaction.quantity_delta), 0)::NUMERIC(14, 3) AS current_quantity,
    (
        truck.capacity - COALESCE(SUM(transaction.quantity_delta), 0)
    )::NUMERIC(14, 3) AS available_capacity
FROM fuel_trucks AS truck
LEFT JOIN fuel_inventory_transactions AS transaction
  ON transaction.fuel_truck_identifier = truck.service_vehicle_identifier
GROUP BY truck.service_vehicle_identifier, truck.fuel_type_code,
    truck.capacity, truck.quantity_unit;

CREATE VIEW service_vehicle_current_status AS
SELECT
    vehicle.identifier,
    vehicle.service_vehicle_type_code,
    CASE
        WHEN vehicle.operational_status = 'OUT_OF_SERVICE' THEN 'OUT_OF_SERVICE'
        WHEN task.task_id IS NOT NULL THEN 'AT_AIRCRAFT'
        ELSE 'AVAILABLE'
    END AS current_status,
    visit.tail_number AS current_tail_number,
    task.task_id AS current_task_id
FROM service_vehicles AS vehicle
LEFT JOIN tasks AS task
  ON task.service_vehicle_identifier = vehicle.identifier
 AND task.status = 'IN_PROGRESS'
LEFT JOIN aircraft_visits AS visit
  ON visit.visit_id = task.aircraft_visit_id;

CREATE VIEW worker_current_status AS
SELECT
    worker.worker_id,
    worker.first_name,
    worker.last_name,
    worker.role_name,
    (shift.shift_id IS NOT NULL) AS is_at_work,
    task.task_id AS current_task_id,
    task.title AS current_task_title
FROM workers AS worker
LEFT JOIN worker_shifts AS shift
  ON shift.worker_id = worker.worker_id
 AND shift.status = 'IN_PROGRESS'
LEFT JOIN tasks AS task
  ON task.assigned_worker_id = worker.worker_id
 AND task.status = 'IN_PROGRESS';

COMMIT;
