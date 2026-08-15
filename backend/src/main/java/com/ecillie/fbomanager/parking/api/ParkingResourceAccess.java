package com.ecillie.fbomanager.parking.api;

import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingSpot;

/** Transaction-aware parking access used by visit workflows. */
public interface ParkingResourceAccess {

	ParkingSpot lockAvailableSpot(String spotCode);
}
