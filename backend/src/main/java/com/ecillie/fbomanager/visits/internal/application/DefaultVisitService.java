package com.ecillie.fbomanager.visits.internal.application;

import com.ecillie.fbomanager.aircraft.api.AircraftResourceAccess;
import com.ecillie.fbomanager.parking.api.ParkingResourceAccess;
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
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest.Direction;
import com.ecillie.fbomanager.visits.api.VisitModels.AircraftVisit;
import com.ecillie.fbomanager.visits.api.VisitModels.OperationalVisitDetail;
import com.ecillie.fbomanager.visits.api.VisitModels.VisitDetail;
import com.ecillie.fbomanager.visits.api.VisitModels.VisitFilter;
import com.ecillie.fbomanager.visits.api.VisitModels.VisitStatus;
import com.ecillie.fbomanager.visits.api.VisitRepository;
import com.ecillie.fbomanager.visits.api.VisitResourceAccess;
import com.ecillie.fbomanager.visits.api.VisitService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultVisitService implements VisitService, VisitResourceAccess {

	private static final Duration RETENTION = Duration.ofHours(24);
	private final VisitRepository repository;
	private final AircraftResourceAccess aircraft;
	private final ParkingResourceAccess parking;
	private final IdempotentCommandExecutor idempotency;

	public DefaultVisitService(VisitRepository repository, AircraftResourceAccess aircraft,
			ParkingResourceAccess parking, IdempotentCommandExecutor idempotency) {
		this.repository = repository;
		this.aircraft = aircraft;
		this.parking = parking;
		this.idempotency = idempotency;
	}

	@Override
	@Transactional
	public IdempotentResult<AircraftVisit> create(CreateVisit command, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.VISITS_WRITE);
		validateEstimatedTimes(command.estimatedArrivalAt(), command.estimatedDepartureAt());
		return this.idempotency.execute("createAircraftVisit", key, command, actor, 201, RETENTION, AircraftVisit.class,
				() -> write(() -> {
					this.aircraft.lockActiveAircraft(command.tailNumber());
					return this.repository.save(new AircraftVisit(null, command.tailNumber(), null,
							VisitStatus.EXPECTED, command.estimatedArrivalAt(), null, command.estimatedDepartureAt(),
							null, command.notes(), null));
				}));
	}

	@Override
	@Transactional
	public IdempotentResult<AircraftVisit> markInbound(long visitId, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.VISITS_WRITE);
		return execute("markAircraftVisitInbound", key, visitId, actor, () -> {
			AircraftVisit visit = lock(visitId);
			requireState(visit, VisitStatus.EXPECTED);
			return save(copy(visit, visit.parkingSpotCode(), VisitStatus.INBOUND, visit.actualArrivalAt(),
					visit.actualDepartureAt()));
		});
	}

	@Override
	@Transactional
	public IdempotentResult<AircraftVisit> arrive(ArriveVisit command, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.VISITS_WRITE);
		if (command.actualArrivalAt() == null) {
			throw new DomainValidationException("ARRIVAL_TIME_REQUIRED", "An actual arrival time is required.");
		}
		return execute("arriveAircraftVisit", key, command, actor, () -> {
			this.parking.lockAvailableSpot(command.parkingSpotCode());
			AircraftVisit visit = lock(command.visitId());
			requireState(visit, VisitStatus.INBOUND);
			requireSpotUnoccupied(command.parkingSpotCode(), visit.visitId());
			return save(copy(visit, command.parkingSpotCode(), VisitStatus.ON_RAMP, command.actualArrivalAt(), null));
		});
	}

	@Override
	@Transactional
	public IdempotentResult<AircraftVisit> assignParking(AssignParking command, ActorContext actor,
			IdempotencyKey key) {
		actor.require(Capability.VISITS_WRITE);
		return execute("assignAircraftVisitParking", key, command, actor, () -> {
			this.parking.lockAvailableSpot(command.parkingSpotCode());
			AircraftVisit visit = lock(command.visitId());
			requireState(visit, VisitStatus.ON_RAMP);
			requireSpotUnoccupied(command.parkingSpotCode(), visit.visitId());
			return save(copy(visit, command.parkingSpotCode(), visit.status(), visit.actualArrivalAt(),
					visit.actualDepartureAt()));
		});
	}

	@Override
	@Transactional
	public IdempotentResult<AircraftVisit> depart(VisitTransition command, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.VISITS_WRITE);
		if (command.occurredAt() == null) {
			throw new DomainValidationException("DEPARTURE_TIME_REQUIRED", "An actual departure time is required.");
		}
		return execute("departAircraftVisit", key, command, actor, () -> {
			AircraftVisit visit = lock(command.visitId());
			requireState(visit, VisitStatus.ON_RAMP);
			if (visit.actualArrivalAt() != null && command.occurredAt().isBefore(visit.actualArrivalAt())) {
				throw new DomainValidationException("VISIT_TIME_ORDER_INVALID",
						"The departure time cannot precede the arrival time.");
			}
			return save(copy(visit, null, VisitStatus.DEPARTED, visit.actualArrivalAt(), command.occurredAt()));
		});
	}

	@Override
	@Transactional
	public IdempotentResult<AircraftVisit> cancel(long visitId, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.VISITS_WRITE);
		return execute("cancelAircraftVisit", key, visitId, actor, () -> {
			AircraftVisit visit = lock(visitId);
			if (visit.status() == VisitStatus.DEPARTED || visit.status() == VisitStatus.CANCELLED) {
				throw stateConflict(visit, "cancelled");
			}
			return save(copy(visit, null, VisitStatus.CANCELLED, visit.actualArrivalAt(), visit.actualDepartureAt()));
		});
	}

	@Override
	@Transactional(readOnly = true)
	public VisitDetail detail(long visitId, ActorContext actor) {
		actor.require(Capability.VISITS_READ);
		return this.repository.findDetail(visitId).orElseThrow(() -> notFound(visitId));
	}

	@Override
	@Transactional(readOnly = true)
	public OperationalVisitDetail operationalDetail(long visitId, ActorContext actor) {
		actor.require(Capability.VISITS_READ);
		return this.repository.findOperationalDetail(visitId).orElseThrow(() -> notFound(visitId));
	}

	@Override
	@Transactional(readOnly = true)
	public RepositoryPage<VisitDetail> visits(VisitFilter filter, RepositoryPageRequest page, ActorContext actor) {
		actor.require(Capability.VISITS_READ);
		return this.repository.findOperationalDetails(filter, page);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public AircraftVisit lockActiveVisit(long visitId) {
		AircraftVisit visit = lock(visitId);
		if (visit.status() == VisitStatus.DEPARTED || visit.status() == VisitStatus.CANCELLED) {
			throw new DomainValidationException("VISIT_NOT_ACTIVE", "The aircraft visit is no longer active.",
					Map.of("visitId", Long.toString(visitId)));
		}
		return visit;
	}

	private IdempotentResult<AircraftVisit> execute(String operation, IdempotencyKey key, Object command,
			ActorContext actor, Supplier<AircraftVisit> action) {
		return this.idempotency.execute(operation, key, command, actor, 200, RETENTION, AircraftVisit.class, action);
	}

	private AircraftVisit lock(long visitId) {
		return this.repository.lock(visitId).orElseThrow(() -> notFound(visitId));
	}

	private AircraftVisit save(AircraftVisit visit) {
		return write(() -> this.repository.save(visit));
	}

	private void requireSpotUnoccupied(String spotCode, Long currentVisitId) {
		VisitFilter filter = new VisitFilter(List.of(VisitStatus.ON_RAMP), null, spotCode, null, null);
		RepositoryPageRequest page = new RepositoryPageRequest(0, 1, "visitId", Direction.ASC);
		boolean occupied = this.repository.findOperationalDetails(filter, page).items().stream()
				.anyMatch(detail -> !detail.visit().visitId().equals(currentVisitId));
		if (occupied) {
			throw new DomainConflictException("PARKING_SPOT_OCCUPIED", "The parking spot is already occupied.",
					Map.of("parkingSpotCode", spotCode));
		}
	}

	private static void requireState(AircraftVisit visit, VisitStatus expected) {
		if (visit.status() != expected) {
			throw stateConflict(visit, expected.name());
		}
	}

	private static DomainConflictException stateConflict(AircraftVisit visit, String target) {
		return new DomainConflictException("VISIT_STATE_CONFLICT", "The visit cannot make the requested transition.",
				Map.of("currentStatus", visit.status().name(), "target", target));
	}

	private static void validateEstimatedTimes(Instant arrival, Instant departure) {
		if (arrival == null || departure == null || !arrival.isBefore(departure)) {
			throw new DomainValidationException("VISIT_TIME_ORDER_INVALID",
					"Estimated arrival must precede estimated departure.");
		}
	}

	private static AircraftVisit copy(AircraftVisit visit, String spot, VisitStatus status, Instant arrival,
			Instant departure) {
		return new AircraftVisit(visit.visitId(), visit.tailNumber(), spot, status, visit.estimatedArrivalAt(), arrival,
				visit.estimatedDepartureAt(), departure, visit.notes(), visit.audit());
	}

	private static DomainNotFoundException notFound(long visitId) {
		return new DomainNotFoundException("VISIT_NOT_FOUND", "The aircraft visit was not found.",
				Map.of("visitId", Long.toString(visitId)));
	}

	private static <T> T write(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (PersistenceFailure failure) {
			throw DomainFailures.from(failure, "VISIT_CONFLICT");
		}
	}
}
