package com.ecillie.fbomanager.fleet.api;

import com.ecillie.fbomanager.fleet.api.FleetModels.FuelTruck;
import com.ecillie.fbomanager.fleet.api.FleetModels.ServiceVehicle;

/** Transaction-aware vehicle access used by task and fuel workflows. */
public interface FleetResourceAccess {

	ServiceVehicle lockAssignableVehicle(String identifier);

	ServiceVehicle lockActiveVehicle(String identifier);

	FuelTruck fuelTruck(String identifier);
}
