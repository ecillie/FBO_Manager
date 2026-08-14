package com.ecillie.fbomanager.fleet.api;

import com.ecillie.fbomanager.fleet.api.FleetModels.FuelTruck;
import com.ecillie.fbomanager.fleet.api.FleetModels.ServiceVehicle;
import com.ecillie.fbomanager.fleet.api.FleetModels.ServiceVehicleType;
import com.ecillie.fbomanager.fleet.api.FleetModels.VehicleFilter;
import com.ecillie.fbomanager.fleet.api.FleetModels.VehicleStatus;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import java.util.Optional;

public interface FleetRepository {

	ServiceVehicleType saveType(ServiceVehicleType type);

	ServiceVehicle saveVehicle(ServiceVehicle vehicle);

	FuelTruck saveFuelTruck(FuelTruck truck);

	Optional<ServiceVehicle> findVehicle(String identifier);

	Optional<ServiceVehicle> lockVehicle(String identifier);

	RepositoryPage<ServiceVehicle> findVehicles(VehicleFilter filter, RepositoryPageRequest page);

	RepositoryPage<VehicleStatus> findCurrentStatuses(VehicleFilter filter, RepositoryPageRequest page);
}
