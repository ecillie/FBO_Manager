package com.ecillie.fbomanager.workforce.api;

import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.IdempotencyKey;
import com.ecillie.fbomanager.platform.api.IdempotentResult;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.Role;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.Worker;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerFilter;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerShift;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerStatus;
import java.time.Instant;

public interface WorkforceService {

	record CreateShift(long workerId, Instant scheduledStartAt, Instant scheduledEndAt, String notes) {
	}

	record ShiftTransition(long shiftId, Instant occurredAt) {
	}

	Role saveRole(Role role, ActorContext actor);

	Worker saveWorker(Worker worker, ActorContext actor);

	IdempotentResult<WorkerShift> createShift(CreateShift command, ActorContext actor, IdempotencyKey key);

	IdempotentResult<WorkerShift> startShift(ShiftTransition command, ActorContext actor, IdempotencyKey key);

	IdempotentResult<WorkerShift> completeShift(ShiftTransition command, ActorContext actor, IdempotencyKey key);

	IdempotentResult<WorkerShift> markAbsent(long shiftId, ActorContext actor, IdempotencyKey key);

	IdempotentResult<WorkerShift> cancelShift(long shiftId, ActorContext actor, IdempotencyKey key);

	Worker worker(long workerId, ActorContext actor);

	RepositoryPage<Worker> workers(WorkerFilter filter, RepositoryPageRequest page, ActorContext actor);

	RepositoryPage<WorkerShift> shifts(long workerId, Instant from, Instant before, RepositoryPageRequest page,
			ActorContext actor);

	RepositoryPage<WorkerStatus> currentStatuses(WorkerFilter filter, RepositoryPageRequest page, ActorContext actor);
}
