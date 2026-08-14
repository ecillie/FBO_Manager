package com.ecillie.fbomanager.workforce.api;

import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.Role;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.Worker;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerFilter;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerShift;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerStatus;
import java.time.Instant;
import java.util.Optional;

public interface WorkforceRepository {

	Role saveRole(Role role);

	Worker saveWorker(Worker worker);

	WorkerShift saveShift(WorkerShift shift);

	Optional<Worker> findWorker(long workerId);

	Optional<Worker> lockWorker(long workerId);

	RepositoryPage<Worker> findWorkers(WorkerFilter filter, RepositoryPageRequest page);

	RepositoryPage<WorkerShift> findShifts(long workerId, Instant from, Instant before, RepositoryPageRequest page);

	RepositoryPage<WorkerStatus> findCurrentStatuses(WorkerFilter filter, RepositoryPageRequest page);
}
