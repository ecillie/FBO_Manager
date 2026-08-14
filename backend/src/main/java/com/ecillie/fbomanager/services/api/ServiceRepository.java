package com.ecillie.fbomanager.services.api;

import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceRequest;
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceRequestFilter;
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceType;
import java.util.Optional;

public interface ServiceRepository {

	ServiceType saveType(ServiceType type);

	ServiceRequest saveRequest(ServiceRequest request);

	Optional<ServiceRequest> findRequest(long serviceRequestId);

	Optional<ServiceRequest> lockRequest(long serviceRequestId);

	RepositoryPage<ServiceRequest> findRequests(ServiceRequestFilter filter, RepositoryPageRequest page);
}
