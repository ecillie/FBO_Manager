package com.ecillie.fbomanager.services.api;

import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.FixedPrecisionQuantity;
import com.ecillie.fbomanager.platform.api.IdempotencyKey;
import com.ecillie.fbomanager.platform.api.IdempotentResult;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceRequest;
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceRequestFilter;
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceType;
import java.time.Instant;

public interface ServiceRequestService {

	record CreateServiceRequest(long aircraftVisitId, String serviceTypeCode, String fuelTypeCode,
			FixedPrecisionQuantity requestedQuantity, String quantityUnit, String notes, Instant dueAt) {
	}

	ServiceType saveType(ServiceType type, ActorContext actor);

	IdempotentResult<ServiceRequest> create(CreateServiceRequest command, ActorContext actor, IdempotencyKey key);

	IdempotentResult<ServiceRequest> start(long serviceRequestId, ActorContext actor, IdempotencyKey key);

	IdempotentResult<ServiceRequest> complete(long serviceRequestId, ActorContext actor, IdempotencyKey key);

	IdempotentResult<ServiceRequest> cancel(long serviceRequestId, ActorContext actor, IdempotencyKey key);

	ServiceRequest request(long serviceRequestId, ActorContext actor);

	RepositoryPage<ServiceRequest> requests(ServiceRequestFilter filter, RepositoryPageRequest page,
			ActorContext actor);
}
