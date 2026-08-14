package com.ecillie.fbomanager.visits.api;

import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.IdempotencyKey;
import com.ecillie.fbomanager.platform.api.IdempotentResult;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import com.ecillie.fbomanager.visits.api.VisitModels.AircraftVisit;
import com.ecillie.fbomanager.visits.api.VisitModels.OperationalVisitDetail;
import com.ecillie.fbomanager.visits.api.VisitModels.VisitDetail;
import com.ecillie.fbomanager.visits.api.VisitModels.VisitFilter;
import java.time.Instant;

public interface VisitService {

	record CreateVisit(String tailNumber, Instant estimatedArrivalAt, Instant estimatedDepartureAt, String notes) {
	}

	record VisitTransition(long visitId, Instant occurredAt) {
	}

	record ArriveVisit(long visitId, String parkingSpotCode, Instant actualArrivalAt) {
	}

	record AssignParking(long visitId, String parkingSpotCode) {
	}

	IdempotentResult<AircraftVisit> create(CreateVisit command, ActorContext actor, IdempotencyKey key);

	IdempotentResult<AircraftVisit> markInbound(long visitId, ActorContext actor, IdempotencyKey key);

	IdempotentResult<AircraftVisit> arrive(ArriveVisit command, ActorContext actor, IdempotencyKey key);

	IdempotentResult<AircraftVisit> assignParking(AssignParking command, ActorContext actor, IdempotencyKey key);

	IdempotentResult<AircraftVisit> depart(VisitTransition command, ActorContext actor, IdempotencyKey key);

	IdempotentResult<AircraftVisit> cancel(long visitId, ActorContext actor, IdempotencyKey key);

	VisitDetail detail(long visitId, ActorContext actor);

	OperationalVisitDetail operationalDetail(long visitId, ActorContext actor);

	RepositoryPage<VisitDetail> visits(VisitFilter filter, RepositoryPageRequest page, ActorContext actor);
}
