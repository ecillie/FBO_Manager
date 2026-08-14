package com.ecillie.fbomanager.aircraft.api;

import com.ecillie.fbomanager.platform.api.AuditMetadata;
import com.ecillie.fbomanager.platform.api.NaturalKey;

/** Aircraft catalog and physical-aircraft persistence models. */
public final class AircraftModels {

	private AircraftModels() {
	}

	public record AircraftCategory(String code, String name, String description, AuditMetadata audit) {
		public AircraftCategory {
			code = NaturalKey.code(code);
			name = NaturalKey.name(name);
		}
	}

	public record AircraftOperationType(String code, String name, String description, boolean active,
			AuditMetadata audit) {
		public AircraftOperationType {
			code = NaturalKey.code(code);
			name = NaturalKey.name(name);
		}
	}

	public record AircraftManufacturer(String name, AuditMetadata audit) {
		public AircraftManufacturer {
			name = NaturalKey.name(name);
		}
	}

	public record AircraftModelKey(String manufacturerName, String modelName) {
		public AircraftModelKey {
			manufacturerName = NaturalKey.name(manufacturerName);
			modelName = NaturalKey.name(modelName);
		}
	}

	public record AircraftModel(AircraftModelKey key, String aircraftCategoryCode, String icaoTypeCode, boolean active,
			AuditMetadata audit) {
		public AircraftModel {
			if (key == null) {
				throw new IllegalArgumentException("key must not be null");
			}
			aircraftCategoryCode = NaturalKey.code(aircraftCategoryCode);
			icaoTypeCode = NaturalKey.optionalCode(icaoTypeCode);
		}
	}

	public record Aircraft(String tailNumber, AircraftModelKey modelKey, String operationTypeCode, String fuelTypeCode,
			Long ownerCustomerId, Long operatorCustomerId, String notes, boolean active, AuditMetadata audit) {
		public Aircraft {
			tailNumber = NaturalKey.identifier(tailNumber);
			if (modelKey == null) {
				throw new IllegalArgumentException("modelKey must not be null");
			}
			operationTypeCode = NaturalKey.code(operationTypeCode);
			fuelTypeCode = NaturalKey.code(fuelTypeCode);
		}
	}

	public record AircraftFilter(Boolean active, String operationTypeCode, String categoryCode, String query) {
		public AircraftFilter {
			operationTypeCode = NaturalKey.optionalCode(operationTypeCode);
			categoryCode = NaturalKey.optionalCode(categoryCode);
			query = query == null ? null : NaturalKey.text(query);
		}
	}
}
