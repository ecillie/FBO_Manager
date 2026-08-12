-- Generic catalogs are application-owned and safe to rerun. Airport-specific
-- settings, layouts, aircraft, vehicles, tanks, and workers are never seeded.
INSERT INTO fuel_types (code, name, default_unit, is_active)
VALUES
    ('JET_A', 'Jet A', 'US_GALLON', TRUE),
    ('AVGAS_100LL', '100LL Avgas', 'US_GALLON', TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    default_unit = EXCLUDED.default_unit,
    is_active = EXCLUDED.is_active
WHERE (fuel_types.name, fuel_types.default_unit, fuel_types.is_active)
    IS DISTINCT FROM (EXCLUDED.name, EXCLUDED.default_unit, EXCLUDED.is_active);

INSERT INTO aircraft_categories (code, name, description)
VALUES
    ('JET', 'Jet', 'Fixed-wing aircraft powered by one or more jet engines.'),
    ('TURBOPROP', 'Turboprop', 'Fixed-wing aircraft powered by one or more turboprop engines.'),
    ('PISTON', 'Piston', 'Fixed-wing aircraft powered by one or more piston engines.'),
    ('HELICOPTER', 'Helicopter', 'Rotary-wing helicopter aircraft.')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description
WHERE (aircraft_categories.name, aircraft_categories.description)
    IS DISTINCT FROM (EXCLUDED.name, EXCLUDED.description);

INSERT INTO aircraft_operation_types (code, name, description, is_active)
VALUES
    ('COMMERCIAL', 'Commercial', 'Commercial passenger or cargo operation.', TRUE),
    ('GENERAL_AVIATION', 'General Aviation', 'Private, business, training, or other general aviation operation.', TRUE),
    ('MEDICAL', 'Medical', 'Medical transport or emergency medical operation.', TRUE),
    ('MILITARY', 'Military', 'Military or government defense operation.', TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active
WHERE (aircraft_operation_types.name, aircraft_operation_types.description, aircraft_operation_types.is_active)
    IS DISTINCT FROM (EXCLUDED.name, EXCLUDED.description, EXCLUDED.is_active);

INSERT INTO service_types (code, name, is_fuel_service, default_unit, is_active)
VALUES
    ('FUEL', 'Fuel', TRUE, 'US_GALLON', TRUE),
    ('GPU', 'Ground Power Unit', FALSE, NULL, TRUE),
    ('LAVATORY', 'Lavatory Service', FALSE, NULL, TRUE),
    ('PASSENGER_PICKUP', 'Passenger Pickup', FALSE, NULL, TRUE),
    ('PASSENGER_DROPOFF', 'Passenger Drop-off', FALSE, NULL, TRUE),
    ('CATERING_PICKUP', 'Catering Pickup', FALSE, NULL, TRUE),
    ('CATERING_DROPOFF', 'Catering Drop-off', FALSE, NULL, TRUE),
    ('MISCELLANEOUS', 'Miscellaneous', FALSE, NULL, TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_fuel_service = EXCLUDED.is_fuel_service,
    default_unit = EXCLUDED.default_unit,
    is_active = EXCLUDED.is_active
WHERE (service_types.name, service_types.is_fuel_service, service_types.default_unit, service_types.is_active)
    IS DISTINCT FROM (EXCLUDED.name, EXCLUDED.is_fuel_service, EXCLUDED.default_unit, EXCLUDED.is_active);

INSERT INTO service_vehicle_types (code, name, is_fuel_truck)
VALUES
    ('FUEL_TRUCK', 'Fuel Truck', TRUE),
    ('GPU', 'Ground Power Unit', FALSE),
    ('TUG', 'Tug', FALSE),
    ('TOW_VEHICLE', 'Tow Vehicle', FALSE),
    ('LAVATORY', 'Lavatory Vehicle', FALSE),
    ('OTHER', 'Other', FALSE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_fuel_truck = EXCLUDED.is_fuel_truck
WHERE (service_vehicle_types.name, service_vehicle_types.is_fuel_truck)
    IS DISTINCT FROM (EXCLUDED.name, EXCLUDED.is_fuel_truck);

INSERT INTO roles (name, description)
VALUES
    ('ADMINISTRATOR', 'Manages airport configuration, security, and all operational capabilities.'),
    ('DISPATCHER', 'Coordinates visits, parking, services, vehicles, and task dispatch.'),
    ('FUEL_MANAGER', 'Manages fuel inventory, reconciliation, and inventory audit review.'),
    ('WORKFORCE_MANAGER', 'Manages workers, shifts, attendance, and assignments.'),
    ('OPERATOR', 'Performs assigned line-service, fueling, and task work.'),
    ('VIEWER', 'Reads authorized operational information without mutation authority.')
ON CONFLICT (name) DO UPDATE
SET description = EXCLUDED.description
WHERE roles.description IS DISTINCT FROM EXCLUDED.description;
