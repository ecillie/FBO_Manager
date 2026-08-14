package com.ecillie.fbomanager.visits.api;

import com.ecillie.fbomanager.visits.api.VisitModels.AircraftVisit;

/** Transaction-aware visit access used by service workflows. */
public interface VisitResourceAccess {

	AircraftVisit lockActiveVisit(long visitId);
}
