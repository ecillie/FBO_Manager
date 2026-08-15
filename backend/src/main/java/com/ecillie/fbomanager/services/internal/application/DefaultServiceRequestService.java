package com.ecillie.fbomanager.services.internal.application;

import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.AirportTime;
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
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceRequest;
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceRequestFilter;
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceRequestStatus;
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceType;
import com.ecillie.fbomanager.services.api.ServiceRepository;
import com.ecillie.fbomanager.services.api.ServiceRequestService;
import com.ecillie.fbomanager.services.api.ServiceTaskRequested;
import com.ecillie.fbomanager.services.api.ServiceWorkflow;
import com.ecillie.fbomanager.visits.api.VisitResourceAccess;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultServiceRequestService implements ServiceRequestService, ServiceWorkflow {

	private static final Duration RETENTION = Duration.ofHours(24);
	private final ServiceRepository repository;
	private final VisitResourceAccess visits;
	private final IdempotentCommandExecutor idempotency;
	private final ApplicationEventPublisher events;
	private final AirportTime time;

	public DefaultServiceRequestService(ServiceRepository repository, VisitResourceAccess visits,
			IdempotentCommandExecutor idempotency, ApplicationEventPublisher events, AirportTime time) {
		this.repository = repository;
		this.visits = visits;
		this.idempotency = idempotency;
		this.events = events;
		this.time = time;
	}

	@Override
	@Transactional
	public ServiceType saveType(ServiceType type, ActorContext actor) {
		actor.require(Capability.SERVICES_WRITE);
		return write(() -> this.repository.saveType(type));
	}

	@Override
	@Transactional
	public IdempotentResult<ServiceRequest> create(CreateServiceRequest command, ActorContext actor,
			IdempotencyKey key) {
		actor.require(Capability.SERVICES_WRITE);
		return this.idempotency.execute("createServiceRequest", key, command, actor, 201, RETENTION,
				ServiceRequest.class, () -> write(() -> {
					this.visits.lockActiveVisit(command.aircraftVisitId());
					ServiceType type = this.repository.findType(command.serviceTypeCode())
							.orElseThrow(() -> new DomainNotFoundException("SERVICE_TYPE_NOT_FOUND",
									"The service type was not found."));
					validate(type, command);
					ServiceRequest request = this.repository
							.saveRequest(new ServiceRequest(null, command.aircraftVisitId(), command.serviceTypeCode(),
									command.fuelTypeCode(), ServiceRequestStatus.REQUESTED, command.requestedQuantity(),
									command.quantityUnit(), command.notes(), null, null));
					this.events.publishEvent(new ServiceTaskRequested(request.serviceRequestId(),
							request.aircraftVisitId(), type.name(), command.dueAt()));
					return request;
				}));
	}

	@Override
	@Transactional
	public IdempotentResult<ServiceRequest> start(long serviceRequestId, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.SERVICES_WRITE);
		return execute("startServiceRequest", serviceRequestId, actor, key, () -> transition(lock(serviceRequestId),
				ServiceRequestStatus.REQUESTED, ServiceRequestStatus.IN_PROGRESS));
	}

	@Override
	@Transactional
	public IdempotentResult<ServiceRequest> complete(long serviceRequestId, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.SERVICES_WRITE);
		return execute("completeServiceRequest", serviceRequestId, actor, key, () -> transition(lock(serviceRequestId),
				ServiceRequestStatus.IN_PROGRESS, ServiceRequestStatus.COMPLETED));
	}

	@Override
	@Transactional
	public IdempotentResult<ServiceRequest> cancel(long serviceRequestId, ActorContext actor, IdempotencyKey key) {
		actor.require(Capability.SERVICES_WRITE);
		return execute("cancelServiceRequest", serviceRequestId, actor, key, () -> {
			ServiceRequest request = lock(serviceRequestId);
			if (request.status() == ServiceRequestStatus.COMPLETED
					|| request.status() == ServiceRequestStatus.CANCELLED) {
				throw stateConflict(request, ServiceRequestStatus.CANCELLED);
			}
			return save(copy(request, ServiceRequestStatus.CANCELLED, null));
		});
	}

	@Override
	@Transactional(readOnly = true)
	public ServiceRequest request(long serviceRequestId, ActorContext actor) {
		actor.require(Capability.SERVICES_READ);
		return find(serviceRequestId);
	}

	@Override
	@Transactional(readOnly = true)
	public RepositoryPage<ServiceRequest> requests(ServiceRequestFilter filter, RepositoryPageRequest page,
			ActorContext actor) {
		actor.require(Capability.SERVICES_READ);
		return this.repository.findRequests(filter, page);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public ServiceRequest lockForWork(long serviceRequestId) {
		ServiceRequest request = lock(serviceRequestId);
		if (request.status() != ServiceRequestStatus.REQUESTED
				&& request.status() != ServiceRequestStatus.IN_PROGRESS) {
			throw stateConflict(request, ServiceRequestStatus.IN_PROGRESS);
		}
		return request;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public ServiceRequest startForTask(long serviceRequestId) {
		return transition(lock(serviceRequestId), ServiceRequestStatus.REQUESTED, ServiceRequestStatus.IN_PROGRESS);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public ServiceRequest completeForTask(long serviceRequestId) {
		return transition(lock(serviceRequestId), ServiceRequestStatus.IN_PROGRESS, ServiceRequestStatus.COMPLETED);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public ServiceRequest cancelForTask(long serviceRequestId) {
		ServiceRequest request = lock(serviceRequestId);
		if (request.status() == ServiceRequestStatus.COMPLETED || request.status() == ServiceRequestStatus.CANCELLED) {
			throw stateConflict(request, ServiceRequestStatus.CANCELLED);
		}
		return save(copy(request, ServiceRequestStatus.CANCELLED, null));
	}

	private IdempotentResult<ServiceRequest> execute(String operation, long serviceRequestId, ActorContext actor,
			IdempotencyKey key, Supplier<ServiceRequest> action) {
		return this.idempotency.execute(operation, key, serviceRequestId, actor, 200, RETENTION, ServiceRequest.class,
				action);
	}

	private ServiceRequest transition(ServiceRequest request, ServiceRequestStatus source,
			ServiceRequestStatus target) {
		if (request.status() != source) {
			throw stateConflict(request, target);
		}
		return save(copy(request, target, target == ServiceRequestStatus.COMPLETED ? this.time.now() : null));
	}

	private ServiceRequest find(long id) {
		return this.repository.findRequest(id).orElseThrow(() -> notFound(id));
	}

	private ServiceRequest lock(long id) {
		return this.repository.lockRequest(id).orElseThrow(() -> notFound(id));
	}

	private ServiceRequest save(ServiceRequest request) {
		return write(() -> this.repository.saveRequest(request));
	}

	private static void validate(ServiceType type, CreateServiceRequest command) {
		if (!type.active()) {
			throw new DomainValidationException("SERVICE_TYPE_INACTIVE", "The service type is not active.");
		}
		if (type.fuelService()) {
			if (command.fuelTypeCode() == null || command.requestedQuantity() == null || command.quantityUnit() == null
					|| command.requestedQuantity().toBigDecimal().signum() <= 0) {
				throw new DomainValidationException("FUEL_REQUEST_INVALID",
						"Fuel services require a fuel type, positive quantity, and unit.");
			}
		} else if (command.fuelTypeCode() != null) {
			throw new DomainValidationException("FUEL_REQUEST_INVALID", "Only fuel services may specify a fuel type.");
		}
	}

	private static ServiceRequest copy(ServiceRequest request, ServiceRequestStatus status,
			java.time.Instant completedAt) {
		return new ServiceRequest(request.serviceRequestId(), request.aircraftVisitId(), request.serviceTypeCode(),
				request.fuelTypeCode(), status, request.requestedQuantity(), request.quantityUnit(), request.notes(),
				completedAt, request.audit());
	}

	private static DomainConflictException stateConflict(ServiceRequest request, ServiceRequestStatus target) {
		return new DomainConflictException("SERVICE_REQUEST_STATE_CONFLICT",
				"The service request cannot make the requested transition.",
				Map.of("currentStatus", request.status().name(), "target", target.name()));
	}

	private static DomainNotFoundException notFound(long id) {
		return new DomainNotFoundException("SERVICE_REQUEST_NOT_FOUND", "The service request was not found.",
				Map.of("serviceRequestId", Long.toString(id)));
	}

	private static <T> T write(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (PersistenceFailure failure) {
			throw DomainFailures.from(failure, "SERVICE_REQUEST_CONFLICT");
		}
	}
}
