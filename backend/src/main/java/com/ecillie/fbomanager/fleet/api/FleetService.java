package com.ecillie.fbomanager.fleet.api;

import com.ecillie.fbomanager.fleet.api.FleetModels.FuelTruck;
import com.ecillie.fbomanager.fleet.api.FleetModels.ServiceVehicle;
import com.ecillie.fbomanager.fleet.api.FleetModels.ServiceVehicleType;
import com.ecillie.fbomanager.fleet.api.FleetModels.VehicleFilter;
import com.ecillie.fbomanager.fleet.api.FleetModels.VehicleStatus;
import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;

public interface FleetService {

	ServiceVehicleType saveType(ServiceVehicleType type, ActorContext actor);

	ServiceVehicle saveVehicle(ServiceVehicle vehicle, ActorContext actor);

	FuelTruck saveFuelTruck(FuelTruck truck, ActorContext actor);

	ServiceVehicle vehicle(String identifier, ActorContext actor);

	RepositoryPage<ServiceVehicle> vehicles(VehicleFilter filter, RepositoryPageRequest page, ActorContext actor);

	RepositoryPage<VehicleStatus> currentStatuses(VehicleFilter filter, RepositoryPageRequest page, ActorContext actor);
}
