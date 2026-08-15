package com.ecillie.fbomanager.tasks.internal.application;

import com.ecillie.fbomanager.fleet.api.FleetResourceAccess;
import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.Capability;
import com.ecillie.fbomanager.platform.api.DomainConflictException;
import com.ecillie.fbomanager.platform.api.DomainFailures;
import com.ecillie.fbomanager.platform.api.DomainNotFoundException;
import com.ecillie.fbomanager.platform.api.DomainValidationException;
import com.ecillie.fbomanager.platform.api.IdempotencyKey;
import com.ecillie.fbomanager.platform.api.IdempotentCommandExecutor;
import com.ecillie.fbomanager.platform.api.IdempotentResult;
import com.ecillie.fbomanager.platform.api.PersistenceFailure;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import com.ecillie.fbomanager.services.api.ServiceTaskRequested;
import com.ecillie.fbomanager.services.api.ServiceWorkflow;
import com.ecillie.fbomanager.tasks.api.TaskModels.AirportTask;
import com.ecillie.fbomanager.tasks.api.TaskModels.TaskFilter;
import com.ecillie.fbomanager.tasks.api.TaskModels.TaskStatus;
import com.ecillie.fbomanager.tasks.api.TaskRepository;
import com.ecillie.fbomanager.tasks.api.TaskService;
import com.ecillie.fbomanager.workforce.api.WorkforceResourceAccess;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultTaskService implements TaskService {

	private static final Duration RETENTION = Duration.ofHours(24);
	private final TaskRepository repository;
	private final WorkforceResourceAccess workforce;
	private final FleetResourceAccess fleet;
	private final ServiceWorkflow services;
	private final IdempotentCommandExecutor idempotency;

	public DefaultTaskService(TaskRepository repository, WorkforceResourceAccess workforce, FleetResourceAccess fleet,
			ServiceWorkflow services, IdempotentCommandExecutor idempotency) {
		this.repository = repository;
		this.workforce = workforce;
		this.fleet = fleet;
		this.services = services;
		this.idempotency = idempotency;
	}

	@Override
	@Transactional
	public IdempotentResult<AirportTask> create(CreateTask command, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.TASKS_WRITE);
		return this.idempotency.execute("createTask", key, command, actor, 201, RETENTION, AirportTask.class,
				() -> save(new AirportTask(null, command.aircraftVisitId(), command.serviceRequestId(), null, null,
						command.title(), command.description(), TaskStatus.PENDING, command.dueAt(), null, null,
						null)));
	}

	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void createLinkedServiceTask(ServiceTaskRequested event) {
		save(new AirportTask(null, event.aircraftVisitId(), event.serviceRequestId(), null, null, event.title(), null,
				TaskStatus.PENDING, event.dueAt(), null, null, null));
	}

	@Override
	@Transactional
	public IdempotentResult<AirportTask> dispatchAndStart(DispatchTask command, ActorContext actor,
			IdempotencyKey key) {
		actor.require(Capability.TASKS_WRITE);
		if (command.startedAt() == null) {
			throw new DomainValidationException("TASK_START_REQUIRED", "An actual task start time is required.");
		}
		return execute("dispatchTask", command, actor, key, () -> {
			this.workforce.lockAssignableWorker(command.workerId());
			if (command.serviceVehicleIdentifier() != null) {
				this.fleet.lockAssignableVehicle(command.serviceVehicleIdentifier());
			}
			AirportTask task = lock(command.taskId());
			requireState(task, TaskStatus.PENDING, TaskStatus.IN_PROGRESS);
			if (task.serviceRequestId() != null) {
				this.services.startForTask(task.serviceRequestId());
			}
			return save(copy(task, command.serviceVehicleIdentifier(), command.workerId(), TaskStatus.IN_PROGRESS,
					command.startedAt(), null));
		});
	}

	@Override
	@Transactional
	public IdempotentResult<AirportTask> complete(CompleteTask command, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.TASKS_WRITE);
		if (command.completedAt() == null) {
			throw new DomainValidationException("TASK_COMPLETION_REQUIRED",
					"An actual task completion time is required.");
		}
		return execute("completeTask", command, actor, key, () -> {
			AirportTask task = lock(command.taskId());
			requireState(task, TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED);
			if (command.completedAt().isBefore(task.startedAt())) {
				throw new DomainValidationException("TASK_TIME_ORDER_INVALID",
						"The task completion time cannot precede its start.");
			}
			if (task.serviceRequestId() != null) {
				this.services.completeForTask(task.serviceRequestId());
			}
			return save(copy(task, task.serviceVehicleIdentifier(), task.assignedWorkerId(), TaskStatus.COMPLETED,
					task.startedAt(), command.completedAt()));
		});
	}

	@Override
	@Transactional
	public IdempotentResult<AirportTask> cancel(long taskId, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.TASKS_WRITE);
		return execute("cancelTask", taskId, actor, key, () -> {
			AirportTask task = lock(taskId);
			if (task.status() == TaskStatus.COMPLETED || task.status() == TaskStatus.CANCELLED) {
				throw stateConflict(task, TaskStatus.CANCELLED);
			}
			if (task.serviceRequestId() != null) {
				this.services.cancelForTask(task.serviceRequestId());
			}
			return save(copy(task, task.serviceVehicleIdentifier(), task.assignedWorkerId(), TaskStatus.CANCELLED,
					task.startedAt(), null));
		});
	}

	@Override
	@Transactional(readOnly = true)
	public AirportTask task(long taskId, ActorContext actor) {
		actor.require(Capability.TASKS_READ);
		return find(taskId);
	}

	@Override
	@Transactional(readOnly = true)
	public RepositoryPage<AirportTask> tasks(TaskFilter filter, RepositoryPageRequest page, ActorContext actor) {
		actor.require(Capability.TASKS_READ);
		return this.repository.findTasks(filter, page);
	}

	private IdempotentResult<AirportTask> execute(String operation, Object command, ActorContext actor,
			IdempotencyKey key, Supplier<AirportTask> action) {
		return this.idempotency.execute(operation, key, command, actor, 200, RETENTION, AirportTask.class, action);
	}

	private AirportTask find(long taskId) {
		return this.repository.find(taskId).orElseThrow(() -> notFound(taskId));
	}

	private AirportTask lock(long taskId) {
		return this.repository.lockForAssignment(taskId).orElseThrow(() -> notFound(taskId));
	}

	private AirportTask save(AirportTask task) {
		try {
			return this.repository.save(task);
		} catch (PersistenceFailure failure) {
			throw DomainFailures.from(failure, "TASK_RESOURCE_CONFLICT");
		}
	}

	private static void requireState(AirportTask task, TaskStatus source, TaskStatus target) {
		if (task.status() != source) {
			throw stateConflict(task, target);
		}
	}

	private static DomainConflictException stateConflict(AirportTask task, TaskStatus target) {
		return new DomainConflictException("TASK_STATE_CONFLICT", "The task cannot make the requested transition.",
				Map.of("currentStatus", task.status().name(), "target", target.name()));
	}

	private static AirportTask copy(AirportTask task, String vehicle, Long worker, TaskStatus status, Instant started,
			Instant completed) {
		return new AirportTask(task.taskId(), task.aircraftVisitId(), task.serviceRequestId(), vehicle, worker,
				task.title(), task.description(), status, task.dueAt(), started, completed, task.audit());
	}

	private static DomainNotFoundException notFound(long taskId) {
		return new DomainNotFoundException("TASK_NOT_FOUND", "The task was not found.",
				Map.of("taskId", Long.toString(taskId)));
	}
}
