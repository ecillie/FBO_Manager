package com.ecillie.fbomanager.workforce.api;

import com.ecillie.fbomanager.workforce.api.WorkforceModels.Worker;

/** Transaction-aware worker access used by task and fuel workflows. */
public interface WorkforceResourceAccess {

	Worker lockAssignableWorker(long workerId);

	Worker lockActiveWorker(long workerId);
}
