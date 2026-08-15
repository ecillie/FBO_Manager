package com.ecillie.fbomanager.workforce.internal.application;

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
import com.ecillie.fbomanager.workforce.api.WorkforceModels.Role;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.Worker;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerFilter;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerShift;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerShiftStatus;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerStatus;
import com.ecillie.fbomanager.workforce.api.WorkforceRepository;
import com.ecillie.fbomanager.workforce.api.WorkforceResourceAccess;
import com.ecillie.fbomanager.workforce.api.WorkforceService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultWorkforceService implements WorkforceService, WorkforceResourceAccess {

	private static final Duration RETENTION = Duration.ofHours(24);
	private final WorkforceRepository repository;
	private final IdempotentCommandExecutor idempotency;

	public DefaultWorkforceService(WorkforceRepository repository, IdempotentCommandExecutor idempotency) {
		this.repository = repository;
		this.idempotency = idempotency;
	}

	@Override
	@Transactional
	public Role saveRole(Role role, ActorContext actor) {
		actor.require(Capability.WORKFORCE_WRITE);
		return write(() -> this.repository.saveRole(role));
	}

	@Override
	@Transactional
	public Worker saveWorker(Worker worker, ActorContext actor) {
		actor.require(Capability.WORKFORCE_WRITE);
		return write(() -> this.repository.saveWorker(worker));
	}

	@Override
	@Transactional
	public IdempotentResult<WorkerShift> createShift(CreateShift command, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.WORKFORCE_WRITE);
		if (command.scheduledStartAt() == null || command.scheduledEndAt() == null
				|| !command.scheduledStartAt().isBefore(command.scheduledEndAt())) {
			throw new DomainValidationException("SHIFT_TIME_ORDER_INVALID",
					"The scheduled shift start must precede its end.");
		}
		return this.idempotency.execute("createWorkerShift", key, command, actor, 201, RETENTION, WorkerShift.class,
				() -> write(() -> {
					lockActiveWorker(command.workerId());
					return this.repository.saveShift(new WorkerShift(null, command.workerId(),
							command.scheduledStartAt(), command.scheduledEndAt(), null, null,
							WorkerShiftStatus.SCHEDULED, command.notes(), null));
				}));
	}

	@Override
	@Transactional
	public IdempotentResult<WorkerShift> startShift(ShiftTransition command, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.WORKFORCE_WRITE);
		if (command.occurredAt() == null) {
			throw new DomainValidationException("SHIFT_START_REQUIRED", "An actual shift start time is required.");
		}
		return execute("startWorkerShift", command, actor, key, () -> {
			WorkerShift initial = this.repository.findShift(command.shiftId())
					.orElseThrow(() -> notFound(command.shiftId()));
			lockActiveWorker(initial.workerId());
			WorkerShift shift = lockShift(command.shiftId());
			requireState(shift, WorkerShiftStatus.SCHEDULED, WorkerShiftStatus.IN_PROGRESS);
			return save(copy(shift, WorkerShiftStatus.IN_PROGRESS, command.occurredAt(), null));
		});
	}

	@Override
	@Transactional
	public IdempotentResult<WorkerShift> completeShift(ShiftTransition command, ActorContext actor,
			IdempotencyKey key) {
		actor.require(Capability.WORKFORCE_WRITE);
		if (command.occurredAt() == null) {
			throw new DomainValidationException("SHIFT_END_REQUIRED", "An actual shift end time is required.");
		}
		return execute("completeWorkerShift", command, actor, key, () -> {
			WorkerShift shift = lockShift(command.shiftId());
			requireState(shift, WorkerShiftStatus.IN_PROGRESS, WorkerShiftStatus.COMPLETED);
			if (command.occurredAt().isBefore(shift.actualStartAt())) {
				throw new DomainValidationException("SHIFT_TIME_ORDER_INVALID",
						"The actual shift end cannot precede its start.");
			}
			return save(copy(shift, WorkerShiftStatus.COMPLETED, shift.actualStartAt(), command.occurredAt()));
		});
	}

	@Override
	@Transactional
	public IdempotentResult<WorkerShift> markAbsent(long shiftId, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.WORKFORCE_WRITE);
		return execute("markWorkerShiftAbsent", shiftId, actor, key, () -> {
			WorkerShift shift = lockShift(shiftId);
			requireState(shift, WorkerShiftStatus.SCHEDULED, WorkerShiftStatus.ABSENT);
			return save(copy(shift, WorkerShiftStatus.ABSENT, null, null));
		});
	}

	@Override
	@Transactional
	public IdempotentResult<WorkerShift> cancelShift(long shiftId, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.WORKFORCE_WRITE);
		return execute("cancelWorkerShift", shiftId, actor, key, () -> {
			WorkerShift shift = lockShift(shiftId);
			requireState(shift, WorkerShiftStatus.SCHEDULED, WorkerShiftStatus.CANCELLED);
			return save(copy(shift, WorkerShiftStatus.CANCELLED, null, null));
		});
	}

	@Override
	@Transactional(readOnly = true)
	public Worker worker(long workerId, ActorContext actor) {
		actor.require(Capability.WORKFORCE_READ);
		return requiredWorker(workerId, false);
	}

	@Override
	@Transactional(readOnly = true)
	public RepositoryPage<Worker> workers(WorkerFilter filter, RepositoryPageRequest page, ActorContext actor) {
		actor.require(Capability.WORKFORCE_READ);
		return this.repository.findWorkers(filter, page);
	}

	@Override
	@Transactional(readOnly = true)
	public RepositoryPage<WorkerShift> shifts(long workerId, Instant from, Instant before, RepositoryPageRequest page,
			ActorContext actor) {
		actor.require(Capability.WORKFORCE_READ);
		requiredWorker(workerId, false);
		return this.repository.findShifts(workerId, from, before, page);
	}

	@Override
	@Transactional(readOnly = true)
	public RepositoryPage<WorkerStatus> currentStatuses(WorkerFilter filter, RepositoryPageRequest page,
			ActorContext actor) {
		actor.require(Capability.WORKFORCE_READ);
		return this.repository.findCurrentStatuses(filter, page);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Worker lockAssignableWorker(long workerId) {
		Worker worker = lockActiveWorker(workerId);
		WorkerStatus status = this.repository.findCurrentStatus(workerId)
				.orElseThrow(() -> new DomainNotFoundException("WORKER_NOT_FOUND", "The worker was not found."));
		if (!status.atWork()) {
			throw new DomainValidationException("WORKER_NOT_AT_WORK", "The worker is not currently at work.",
					Map.of("workerId", Long.toString(workerId)));
		}
		if (status.currentTaskId() != null) {
			throw new DomainConflictException("WORKER_UNAVAILABLE", "The worker is already assigned.",
					Map.of("workerId", Long.toString(workerId)));
		}
		return worker;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Worker lockActiveWorker(long workerId) {
		Worker worker = requiredWorker(workerId, true);
		if (!worker.active()) {
			throw new DomainValidationException("WORKER_INACTIVE", "The worker is not active.",
					Map.of("workerId", Long.toString(workerId)));
		}
		return worker;
	}

	private IdempotentResult<WorkerShift> execute(String operation, Object command, ActorContext actor,
			IdempotencyKey key, Supplier<WorkerShift> action) {
		return this.idempotency.execute(operation, key, command, actor, 200, RETENTION, WorkerShift.class, action);
	}

	private Worker requiredWorker(long workerId, boolean lock) {
		return (lock ? this.repository.lockWorker(workerId) : this.repository.findWorker(workerId))
				.orElseThrow(() -> new DomainNotFoundException("WORKER_NOT_FOUND", "The worker was not found.",
						Map.of("workerId", Long.toString(workerId))));
	}

	private WorkerShift lockShift(long shiftId) {
		return this.repository.lockShift(shiftId).orElseThrow(() -> notFound(shiftId));
	}

	private WorkerShift save(WorkerShift shift) {
		return write(() -> this.repository.saveShift(shift));
	}

	private static void requireState(WorkerShift shift, WorkerShiftStatus source, WorkerShiftStatus target) {
		if (shift.status() != source) {
			throw new DomainConflictException("SHIFT_STATE_CONFLICT",
					"The worker shift cannot make the requested transition.",
					Map.of("currentStatus", shift.status().name(), "target", target.name()));
		}
	}

	private static WorkerShift copy(WorkerShift shift, WorkerShiftStatus status, Instant actualStart,
			Instant actualEnd) {
		return new WorkerShift(shift.shiftId(), shift.workerId(), shift.scheduledStartAt(), shift.scheduledEndAt(),
				actualStart, actualEnd, status, shift.notes(), shift.audit());
	}

	private static DomainNotFoundException notFound(long shiftId) {
		return new DomainNotFoundException("SHIFT_NOT_FOUND", "The worker shift was not found.",
				Map.of("shiftId", Long.toString(shiftId)));
	}

	private static <T> T write(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (PersistenceFailure failure) {
			throw DomainFailures.from(failure, "WORKFORCE_CONFLICT");
		}
	}
}
