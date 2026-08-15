package com.ecillie.fbomanager.fuel.internal.application;

import com.ecillie.fbomanager.fleet.api.FleetResourceAccess;
import com.ecillie.fbomanager.fuel.api.FuelInventoryService;
import com.ecillie.fbomanager.fuel.api.FuelModels.FuelLedgerEntry;
import com.ecillie.fbomanager.fuel.api.FuelModels.FuelTank;
import com.ecillie.fbomanager.fuel.api.FuelModels.FuelTransactionType;
import com.ecillie.fbomanager.fuel.api.FuelModels.FuelType;
import com.ecillie.fbomanager.fuel.api.FuelModels.LedgerFilter;
import com.ecillie.fbomanager.fuel.api.FuelModels.TankBalance;
import com.ecillie.fbomanager.fuel.api.FuelModels.TruckBalance;
import com.ecillie.fbomanager.fuel.api.FuelRepository;
import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.Capability;
import com.ecillie.fbomanager.platform.api.DomainFailures;
import com.ecillie.fbomanager.platform.api.DomainNotFoundException;
import com.ecillie.fbomanager.platform.api.DomainValidationException;
import com.ecillie.fbomanager.platform.api.FixedPrecisionQuantity;
import com.ecillie.fbomanager.platform.api.IdempotencyKey;
import com.ecillie.fbomanager.platform.api.IdempotentCommandExecutor;
import com.ecillie.fbomanager.platform.api.IdempotentResult;
import com.ecillie.fbomanager.platform.api.PersistenceFailure;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceRequest;
import com.ecillie.fbomanager.services.api.ServiceWorkflow;
import com.ecillie.fbomanager.workforce.api.WorkforceResourceAccess;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultFuelInventoryService implements FuelInventoryService {

	private final FuelRepository repository;
	private final FleetResourceAccess fleet;
	private final WorkforceResourceAccess workforce;
	private final ServiceWorkflow services;
	private final IdempotentCommandExecutor idempotency;

	public DefaultFuelInventoryService(FuelRepository repository, FleetResourceAccess fleet,
			WorkforceResourceAccess workforce, ServiceWorkflow services, IdempotentCommandExecutor idempotency) {
		this.repository = repository;
		this.fleet = fleet;
		this.workforce = workforce;
		this.services = services;
		this.idempotency = idempotency;
	}

	@Override
	@Transactional
	public FuelType saveType(FuelType type, ActorContext actor) {
		actor.require(Capability.FUEL_WRITE);
		return write(() -> this.repository.saveType(type));
	}

	@Override
	@Transactional
	public FuelTank saveTank(FuelTank tank, ActorContext actor) {
		actor.require(Capability.FUEL_WRITE);
		FuelType type = this.repository.findType(tank.fuelTypeCode())
				.orElseThrow(() -> new DomainNotFoundException("FUEL_TYPE_NOT_FOUND", "The fuel type was not found."));
		if (!type.active() || !type.defaultUnit().equals(tank.quantityUnit())
				|| tank.capacity().toBigDecimal().signum() <= 0) {
			throw new DomainValidationException("FUEL_TANK_INVALID",
					"The fuel tank must use an active compatible fuel type, unit, and positive capacity.");
		}
		return write(() -> this.repository.saveTank(tank));
	}

	@Override
	@Transactional
	public IdempotentResult<FuelLedgerEntry> receipt(Receipt command, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.FUEL_WRITE);
		requirePositive(command.quantity());
		return inventory("recordFuelReceipt", key, command, actor, () -> {
			TankBalance tank = lockTank(command.tankName());
			this.workforce.lockActiveWorker(command.recordedByWorkerId());
			validateCompatibility(tank.fuelTypeCode(), tank.quantityUnit(), command.quantityUnit());
			return append(tank.fuelTypeCode(), tank.name(), null, null, command.recordedByWorkerId(),
					FuelTransactionType.RECEIPT, command.quantity(), command.quantityUnit(), null, command.occurredAt(),
					command.notes());
		});
	}

	@Override
	@Transactional
	public IdempotentResult<TransferResult> transfer(Transfer command, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.FUEL_WRITE);
		requirePositive(command.quantity());
		return this.idempotency.executeInventory("transferFuelToTruck", key, command, actor, 201, TransferResult.class,
				result -> result.tankEntry().fuelTransactionId(), () -> write(() -> {
					TankBalance tank = lockTank(command.tankName());
					TruckBalance truck = lockTruck(command.truckIdentifier());
					this.fleet.lockActiveVehicle(command.truckIdentifier());
					this.workforce.lockActiveWorker(command.recordedByWorkerId());
					validateCompatibility(tank.fuelTypeCode(), tank.quantityUnit(), command.quantityUnit());
					validateCompatibility(truck.fuelTypeCode(), truck.quantityUnit(), command.quantityUnit());
					if (!tank.fuelTypeCode().equals(truck.fuelTypeCode())) {
						throw compatibility();
					}
					long group = this.repository.nextTransferGroupId();
					FuelLedgerEntry tankEntry = append(tank.fuelTypeCode(), tank.name(), null, null,
							command.recordedByWorkerId(), FuelTransactionType.TRANSFER, negative(command.quantity()),
							command.quantityUnit(), group, command.occurredAt(), command.notes());
					FuelLedgerEntry truckEntry = append(truck.fuelTypeCode(), null, truck.serviceVehicleIdentifier(),
							null, command.recordedByWorkerId(), FuelTransactionType.TRANSFER, command.quantity(),
							command.quantityUnit(), group, command.occurredAt(), command.notes());
					return new TransferResult(tankEntry, truckEntry, tankBalanceRequired(tank.name()),
							truckBalanceRequired(truck.serviceVehicleIdentifier()));
				}));
	}

	@Override
	@Transactional
	public IdempotentResult<FuelLedgerEntry> dispense(Dispense command, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.FUEL_WRITE);
		requirePositive(command.quantity());
		return inventory("dispenseAircraftFuel", key, command, actor, () -> {
			TruckBalance truck = lockTruck(command.truckIdentifier());
			this.fleet.lockActiveVehicle(command.truckIdentifier());
			ServiceRequest request = this.services.lockForWork(command.serviceRequestId());
			this.workforce.lockActiveWorker(command.recordedByWorkerId());
			validateCompatibility(truck.fuelTypeCode(), truck.quantityUnit(), command.quantityUnit());
			if (request.fuelTypeCode() == null || !request.fuelTypeCode().equals(truck.fuelTypeCode())
					|| !request.quantityUnit().equals(command.quantityUnit())) {
				throw compatibility();
			}
			return append(truck.fuelTypeCode(), null, truck.serviceVehicleIdentifier(), request.serviceRequestId(),
					command.recordedByWorkerId(), FuelTransactionType.DISPENSE, negative(command.quantity()),
					command.quantityUnit(), null, command.occurredAt(), command.notes());
		});
	}

	@Override
	@Transactional
	public IdempotentResult<FuelLedgerEntry> adjust(Adjustment command, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.FUEL_WRITE);
		if ((command.tankName() == null) == (command.truckIdentifier() == null) || command.quantityDelta() == null
				|| command.quantityDelta().toBigDecimal().signum() == 0 || command.notes() == null
				|| command.notes().isBlank()) {
			throw new DomainValidationException("FUEL_ADJUSTMENT_INVALID",
					"An adjustment requires one holder, a nonzero quantity, and explanatory notes.");
		}
		return inventory("adjustFuelInventory", key, command, actor, () -> {
			this.workforce.lockActiveWorker(command.recordedByWorkerId());
			if (command.tankName() != null) {
				TankBalance tank = lockTank(command.tankName());
				validateCompatibility(tank.fuelTypeCode(), tank.quantityUnit(), command.quantityUnit());
				return append(tank.fuelTypeCode(), tank.name(), null, null, command.recordedByWorkerId(),
						FuelTransactionType.ADJUSTMENT, command.quantityDelta(), command.quantityUnit(), null,
						command.occurredAt(), command.notes());
			}
			TruckBalance truck = lockTruck(command.truckIdentifier());
			this.fleet.lockActiveVehicle(command.truckIdentifier());
			validateCompatibility(truck.fuelTypeCode(), truck.quantityUnit(), command.quantityUnit());
			return append(truck.fuelTypeCode(), null, truck.serviceVehicleIdentifier(), null,
					command.recordedByWorkerId(), FuelTransactionType.ADJUSTMENT, command.quantityDelta(),
					command.quantityUnit(), null, command.occurredAt(), command.notes());
		});
	}

	@Override
	@Transactional(readOnly = true)
	public TankBalance tankBalance(String tankName, ActorContext actor) {
		actor.require(Capability.FUEL_READ);
		return tankBalanceRequired(tankName);
	}

	@Override
	@Transactional(readOnly = true)
	public TruckBalance truckBalance(String truckIdentifier, ActorContext actor) {
		actor.require(Capability.FUEL_READ);
		return truckBalanceRequired(truckIdentifier);
	}

	@Override
	@Transactional(readOnly = true)
	public RepositoryPage<FuelLedgerEntry> ledger(LedgerFilter filter, RepositoryPageRequest page, ActorContext actor) {
		actor.require(Capability.FUEL_READ);
		return this.repository.findLedger(filter, page);
	}

	private IdempotentResult<FuelLedgerEntry> inventory(String operation, IdempotencyKey key, Object command,
			ActorContext actor, Supplier<FuelLedgerEntry> action) {
		return this.idempotency.executeInventory(operation, key, command, actor, 201, FuelLedgerEntry.class,
				FuelLedgerEntry::fuelTransactionId, () -> write(action));
	}

	private TankBalance lockTank(String name) {
		TankBalance balance = this.repository.lockTankBalance(name)
				.orElseThrow(() -> new DomainNotFoundException("FUEL_TANK_NOT_FOUND", "The fuel tank was not found.",
						Map.of("tankName", name)));
		FuelTank tank = this.repository.findTank(name).orElseThrow();
		if (!tank.active()) {
			throw new DomainValidationException("FUEL_TANK_INACTIVE", "The fuel tank is not active.");
		}
		return balance;
	}

	private TruckBalance lockTruck(String identifier) {
		return this.repository.lockTruckBalance(identifier)
				.orElseThrow(() -> new DomainNotFoundException("FUEL_TRUCK_NOT_FOUND", "The fuel truck was not found.",
						Map.of("vehicleIdentifier", identifier)));
	}

	private FuelLedgerEntry append(String fuelType, String tank, String truck, Long request, long worker,
			FuelTransactionType type, FixedPrecisionQuantity quantity, String unit, Long transfer,
			java.time.Instant occurred, String notes) {
		if (occurred == null) {
			throw new DomainValidationException("FUEL_OCCURRED_AT_REQUIRED",
					"The inventory occurrence time is required.");
		}
		return this.repository.append(new FuelLedgerEntry(null, fuelType, tank, truck, request, worker, type, quantity,
				unit, transfer, occurred, notes, null));
	}

	private TankBalance tankBalanceRequired(String name) {
		return this.repository.findTankBalance(name)
				.orElseThrow(() -> new DomainNotFoundException("FUEL_TANK_NOT_FOUND", "The fuel tank was not found."));
	}

	private TruckBalance truckBalanceRequired(String identifier) {
		return this.repository.findTruckBalance(identifier).orElseThrow(
				() -> new DomainNotFoundException("FUEL_TRUCK_NOT_FOUND", "The fuel truck was not found."));
	}

	private static void validateCompatibility(String fuelType, String holderUnit, String commandUnit) {
		if (commandUnit == null || !holderUnit.equals(commandUnit)) {
			throw compatibility();
		}
	}

	private static DomainValidationException compatibility() {
		return new DomainValidationException("FUEL_COMPATIBILITY_ERROR",
				"The fuel type or quantity unit is incompatible with the inventory holder.");
	}

	private static void requirePositive(FixedPrecisionQuantity quantity) {
		if (quantity == null || quantity.toBigDecimal().signum() <= 0) {
			throw new DomainValidationException("POSITIVE_FUEL_QUANTITY_REQUIRED",
					"A positive fuel quantity is required.");
		}
	}

	private static FixedPrecisionQuantity negative(FixedPrecisionQuantity quantity) {
		return FixedPrecisionQuantity.from(quantity.toBigDecimal().negate());
	}

	private static <T> T write(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (PersistenceFailure failure) {
			throw DomainFailures.from(failure, "FUEL_INVENTORY_CONFLICT");
		}
	}
}
