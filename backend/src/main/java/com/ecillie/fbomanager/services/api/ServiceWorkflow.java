package com.ecillie.fbomanager.services.api;

import com.ecillie.fbomanager.services.api.ServiceModels.ServiceRequest;

/**
 * Transaction-aware service lifecycle access used by task and fuel workflows.
 */
public interface ServiceWorkflow {

	ServiceRequest lockForWork(long serviceRequestId);

	ServiceRequest startForTask(long serviceRequestId);

	ServiceRequest completeForTask(long serviceRequestId);

	ServiceRequest cancelForTask(long serviceRequestId);
}
