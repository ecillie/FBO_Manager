package com.ecillie.fbomanager.fuel.api;

import com.ecillie.fbomanager.platform.api.AuditMetadata;
import com.ecillie.fbomanager.platform.api.FixedPrecisionQuantity;
import com.ecillie.fbomanager.platform.api.NaturalKey;
import java.time.Instant;

public final class FuelModels {

	private FuelModels() {
	}

	public enum FuelTransactionType {
		OPENING_BALANCE, RECEIPT, TRANSFER, DISPENSE, ADJUSTMENT
	}

	public record FuelType(String code, String name, String defaultUnit, boolean active, AuditMetadata audit) {
		public FuelType {
			code = NaturalKey.code(code);
			name = NaturalKey.name(name);
			defaultUnit = NaturalKey.code(defaultUnit);
		}
	}

	public record FuelTank(String name, String fuelTypeCode, FixedPrecisionQuantity capacity, String quantityUnit,
			String notes, boolean active, AuditMetadata audit) {
		public FuelTank {
			name = NaturalKey.name(name);
			fuelTypeCode = NaturalKey.code(fuelTypeCode);
			quantityUnit = NaturalKey.code(quantityUnit);
			if (capacity == null) {
				throw new IllegalArgumentException("capacity must not be null");
			}
		}
	}

	/** Immutable append-only fuel ledger entry. */
	public record FuelLedgerEntry(Long fuelTransactionId, String fuelTypeCode, String fuelTankName,
			String fuelTruckIdentifier, Long serviceRequestId, long recordedByWorkerId,
			FuelTransactionType transactionType, FixedPrecisionQuantity quantityDelta, String quantityUnit,
			Long transferGroupId, Instant occurredAt, String notes, Instant createdAt) {
		public FuelLedgerEntry {
			fuelTypeCode = NaturalKey.code(fuelTypeCode);
			fuelTankName = NaturalKey.optionalName(fuelTankName);
			fuelTruckIdentifier = NaturalKey.optionalCode(fuelTruckIdentifier);
			quantityUnit = NaturalKey.code(quantityUnit);
			if (transactionType == null || quantityDelta == null || occurredAt == null) {
				throw new IllegalArgumentException("transaction type, quantity, and occurredAt are required");
			}
		}
	}

	public record TankBalance(String name, String fuelTypeCode, FixedPrecisionQuantity capacity, String quantityUnit,
			FixedPrecisionQuantity currentQuantity, FixedPrecisionQuantity availableCapacity) {
	}

	public record TruckBalance(String serviceVehicleIdentifier, String fuelTypeCode, FixedPrecisionQuantity capacity,
			String quantityUnit, FixedPrecisionQuantity currentQuantity, FixedPrecisionQuantity availableCapacity) {
	}

	public record LedgerFilter(String fuelTankName, String fuelTruckIdentifier, Long serviceRequestId,
			Instant occurredFrom, Instant occurredBefore) {
		public LedgerFilter {
			fuelTankName = NaturalKey.optionalName(fuelTankName);
			fuelTruckIdentifier = NaturalKey.optionalCode(fuelTruckIdentifier);
			if (occurredFrom != null && occurredBefore != null && !occurredFrom.isBefore(occurredBefore)) {
				throw new IllegalArgumentException("occurredFrom must be before occurredBefore");
			}
		}
	}
}
