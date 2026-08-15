package com.ecillie.fbomanager.fleet.internal.application;

import com.ecillie.fbomanager.fleet.api.FleetModels.FuelTruck;
import com.ecillie.fbomanager.fleet.api.FleetModels.ServiceVehicle;
import com.ecillie.fbomanager.fleet.api.FleetModels.ServiceVehicleType;
import com.ecillie.fbomanager.fleet.api.FleetModels.VehicleCurrentState;
import com.ecillie.fbomanager.fleet.api.FleetModels.VehicleFilter;
import com.ecillie.fbomanager.fleet.api.FleetModels.VehicleStatus;
import com.ecillie.fbomanager.fleet.api.FleetRepository;
import com.ecillie.fbomanager.fleet.api.FleetResourceAccess;
import com.ecillie.fbomanager.fleet.api.FleetService;
import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.Capability;
import com.ecillie.fbomanager.platform.api.DomainConflictException;
import com.ecillie.fbomanager.platform.api.DomainFailures;
import com.ecillie.fbomanager.platform.api.DomainNotFoundException;
import com.ecillie.fbomanager.platform.api.DomainValidationException;
import com.ecillie.fbomanager.platform.api.OperationalStatus;
import com.ecillie.fbomanager.platform.api.PersistenceFailure;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultFleetService implements FleetService, FleetResourceAccess {

	private final FleetRepository repository;

	public DefaultFleetService(FleetRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public ServiceVehicleType saveType(ServiceVehicleType type, ActorContext actor) {
		actor.require(Capability.FLEET_WRITE);
		return write(() -> this.repository.saveType(type));
	}

	@Override
	@Transactional
	public ServiceVehicle saveVehicle(ServiceVehicle vehicle, ActorContext actor) {
		actor.require(Capability.FLEET_WRITE);
		return write(() -> this.repository.saveVehicle(vehicle));
	}

	@Override
	@Transactional
	public FuelTruck saveFuelTruck(FuelTruck truck, ActorContext actor) {
		actor.require(Capability.FLEET_WRITE);
		ServiceVehicle vehicle = requiredVehicle(truck.serviceVehicleIdentifier(), false);
		ServiceVehicleType type = this.repository.findType(vehicle.serviceVehicleTypeCode()).orElseThrow(
				() -> new DomainNotFoundException("VEHICLE_TYPE_NOT_FOUND", "The service vehicle type was not found."));
		if (!type.fuelTruck()) {
			throw new DomainValidationException("VEHICLE_NOT_FUEL_TRUCK",
					"The service vehicle type is not configured for fuel trucks.");
		}
		return write(() -> this.repository.saveFuelTruck(truck));
	}

	@Override
	@Transactional(readOnly = true)
	public ServiceVehicle vehicle(String identifier, ActorContext actor) {
		actor.require(Capability.FLEET_READ);
		return requiredVehicle(identifier, false);
	}

	@Override
	@Transactional(readOnly = true)
	public RepositoryPage<ServiceVehicle> vehicles(VehicleFilter filter, RepositoryPageRequest page,
			ActorContext actor) {
		actor.require(Capability.FLEET_READ);
		return this.repository.findVehicles(filter, page);
	}

	@Override
	@Transactional(readOnly = true)
	public RepositoryPage<VehicleStatus> currentStatuses(VehicleFilter filter, RepositoryPageRequest page,
			ActorContext actor) {
		actor.require(Capability.FLEET_READ);
		return this.repository.findCurrentStatuses(filter, page);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public ServiceVehicle lockAssignableVehicle(String identifier) {
		ServiceVehicle vehicle = lockActiveVehicle(identifier);
		VehicleStatus status = this.repository.findCurrentStatus(identifier).orElseThrow(
				() -> new DomainNotFoundException("VEHICLE_NOT_FOUND", "The service vehicle was not found."));
		if (status.currentStatus() != VehicleCurrentState.AVAILABLE || status.currentTaskId() != null) {
			throw new DomainConflictException("VEHICLE_UNAVAILABLE", "The service vehicle is already assigned.",
					Map.of("vehicleIdentifier", vehicle.identifier()));
		}
		return vehicle;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public ServiceVehicle lockActiveVehicle(String identifier) {
		ServiceVehicle vehicle = requiredVehicle(identifier, true);
		if (!vehicle.active() || vehicle.status() != OperationalStatus.AVAILABLE) {
			throw new DomainValidationException("VEHICLE_OUT_OF_SERVICE", "The service vehicle is not active.",
					Map.of("vehicleIdentifier", vehicle.identifier()));
		}
		return vehicle;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public FuelTruck fuelTruck(String identifier) {
		return this.repository.findFuelTruck(identifier)
				.orElseThrow(() -> new DomainNotFoundException("FUEL_TRUCK_NOT_FOUND", "The fuel truck was not found.",
						Map.of("vehicleIdentifier", identifier)));
	}

	private ServiceVehicle requiredVehicle(String identifier, boolean lock) {
		return (lock ? this.repository.lockVehicle(identifier) : this.repository.findVehicle(identifier))
				.orElseThrow(() -> new DomainNotFoundException("VEHICLE_NOT_FOUND",
						"The service vehicle was not found.", Map.of("vehicleIdentifier", identifier)));
	}

	private static <T> T write(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (PersistenceFailure failure) {
			throw DomainFailures.from(failure, "FLEET_CONFLICT");
		}
	}
}
