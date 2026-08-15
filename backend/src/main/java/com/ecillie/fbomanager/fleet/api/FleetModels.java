package com.ecillie.fbomanager.fleet.api;

import com.ecillie.fbomanager.platform.api.AuditMetadata;
import com.ecillie.fbomanager.platform.api.FixedPrecisionQuantity;
import com.ecillie.fbomanager.platform.api.NaturalKey;
import com.ecillie.fbomanager.platform.api.OperationalStatus;

public final class FleetModels {

	private FleetModels() {
	}

	public enum VehicleCurrentState {
		AVAILABLE, AT_AIRCRAFT, OUT_OF_SERVICE
	}

	public record ServiceVehicleType(String code, String name, boolean fuelTruck, AuditMetadata audit) {
		public ServiceVehicleType {
			code = NaturalKey.code(code);
			name = NaturalKey.name(name);
		}
	}

	public record ServiceVehicle(String identifier, String serviceVehicleTypeCode, OperationalStatus status,
			String notes, boolean active, AuditMetadata audit) {
		public ServiceVehicle {
			identifier = NaturalKey.identifier(identifier);
			serviceVehicleTypeCode = NaturalKey.code(serviceVehicleTypeCode);
			if (status == null) {
				throw new IllegalArgumentException("status must not be null");
			}
		}
	}

	public record FuelTruck(String serviceVehicleIdentifier, String fuelTypeCode, FixedPrecisionQuantity capacity,
			String quantityUnit, AuditMetadata audit) {
		public FuelTruck {
			serviceVehicleIdentifier = NaturalKey.identifier(serviceVehicleIdentifier);
			fuelTypeCode = NaturalKey.code(fuelTypeCode);
			quantityUnit = NaturalKey.code(quantityUnit);
			if (capacity == null) {
				throw new IllegalArgumentException("capacity must not be null");
			}
		}
	}

	public record VehicleStatus(String identifier, String serviceVehicleTypeCode, VehicleCurrentState currentStatus,
			String currentTailNumber, Long currentTaskId) {
	}

	public record VehicleFilter(Boolean active, OperationalStatus operationalStatus, String vehicleTypeCode) {
		public VehicleFilter {
			vehicleTypeCode = NaturalKey.optionalCode(vehicleTypeCode);
		}
	}
}
