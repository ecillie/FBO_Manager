package com.ecillie.fbomanager.aircraft.api;

import com.ecillie.fbomanager.aircraft.api.AircraftModels.Aircraft;

/** Transaction-aware aircraft access used by visit workflows. */
public interface AircraftResourceAccess {

	Aircraft lockActiveAircraft(String tailNumber);
}
