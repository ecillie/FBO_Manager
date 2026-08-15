package com.ecillie.fbomanager.tasks.api;

import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.IdempotencyKey;
import com.ecillie.fbomanager.platform.api.IdempotentResult;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import com.ecillie.fbomanager.tasks.api.TaskModels.AirportTask;
import com.ecillie.fbomanager.tasks.api.TaskModels.TaskFilter;
import java.time.Instant;

public interface TaskService {

	record CreateTask(Long aircraftVisitId, Long serviceRequestId, String title, String description, Instant dueAt) {
	}

	record DispatchTask(long taskId, long workerId, String serviceVehicleIdentifier, Instant startedAt) {
	}

	record CompleteTask(long taskId, Instant completedAt) {
	}

	IdempotentResult<AirportTask> create(CreateTask command, ActorContext actor, IdempotencyKey key);

	IdempotentResult<AirportTask> dispatchAndStart(DispatchTask command, ActorContext actor, IdempotencyKey key);

	IdempotentResult<AirportTask> complete(CompleteTask command, ActorContext actor, IdempotencyKey key);

	IdempotentResult<AirportTask> cancel(long taskId, ActorContext actor, IdempotencyKey key);

	AirportTask task(long taskId, ActorContext actor);

	RepositoryPage<AirportTask> tasks(TaskFilter filter, RepositoryPageRequest page, ActorContext actor);
}
