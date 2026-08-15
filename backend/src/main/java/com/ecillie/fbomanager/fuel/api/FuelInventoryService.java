package com.ecillie.fbomanager.fuel.api;

import com.ecillie.fbomanager.fuel.api.FuelModels.FuelLedgerEntry;
import com.ecillie.fbomanager.fuel.api.FuelModels.FuelTank;
import com.ecillie.fbomanager.fuel.api.FuelModels.FuelType;
import com.ecillie.fbomanager.fuel.api.FuelModels.LedgerFilter;
import com.ecillie.fbomanager.fuel.api.FuelModels.TankBalance;
import com.ecillie.fbomanager.fuel.api.FuelModels.TruckBalance;
import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.FixedPrecisionQuantity;
import com.ecillie.fbomanager.platform.api.IdempotencyKey;
import com.ecillie.fbomanager.platform.api.IdempotentResult;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import java.time.Instant;

public interface FuelInventoryService {

	record Receipt(String tankName, FixedPrecisionQuantity quantity, String quantityUnit, long recordedByWorkerId,
			Instant occurredAt, String notes) {
	}

	record Transfer(String tankName, String truckIdentifier, FixedPrecisionQuantity quantity, String quantityUnit,
			long recordedByWorkerId, Instant occurredAt, String notes) {
	}

	record Dispense(String truckIdentifier, long serviceRequestId, FixedPrecisionQuantity quantity, String quantityUnit,
			long recordedByWorkerId, Instant occurredAt, String notes) {
	}

	record Adjustment(String tankName, String truckIdentifier, FixedPrecisionQuantity quantityDelta,
			String quantityUnit, long recordedByWorkerId, Instant occurredAt, String notes) {
	}

	record TransferResult(FuelLedgerEntry tankEntry, FuelLedgerEntry truckEntry, TankBalance tankBalance,
			TruckBalance truckBalance) {
	}

	FuelType saveType(FuelType type, ActorContext actor);

	FuelTank saveTank(FuelTank tank, ActorContext actor);

	IdempotentResult<FuelLedgerEntry> receipt(Receipt command, ActorContext actor, IdempotencyKey key);

	IdempotentResult<TransferResult> transfer(Transfer command, ActorContext actor, IdempotencyKey key);

	IdempotentResult<FuelLedgerEntry> dispense(Dispense command, ActorContext actor, IdempotencyKey key);

	IdempotentResult<FuelLedgerEntry> adjust(Adjustment command, ActorContext actor, IdempotencyKey key);

	TankBalance tankBalance(String tankName, ActorContext actor);

	TruckBalance truckBalance(String truckIdentifier, ActorContext actor);

	RepositoryPage<FuelLedgerEntry> ledger(LedgerFilter filter, RepositoryPageRequest page, ActorContext actor);
}
