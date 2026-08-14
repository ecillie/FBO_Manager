package com.ecillie.fbomanager.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecillie.fbomanager.fleet.api.FleetResourceAccess;
import com.ecillie.fbomanager.fuel.api.FuelInventoryService.Transfer;
import com.ecillie.fbomanager.fuel.api.FuelModels.FuelLedgerEntry;
import com.ecillie.fbomanager.fuel.api.FuelModels.FuelTank;
import com.ecillie.fbomanager.fuel.api.FuelModels.TankBalance;
import com.ecillie.fbomanager.fuel.api.FuelModels.TruckBalance;
import com.ecillie.fbomanager.fuel.api.FuelRepository;
import com.ecillie.fbomanager.fuel.internal.application.DefaultFuelInventoryService;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingSpot;
import com.ecillie.fbomanager.parking.api.ParkingResourceAccess;
import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.AirportTime;
import com.ecillie.fbomanager.platform.api.Capability;
import com.ecillie.fbomanager.platform.api.DomainConflictException;
import com.ecillie.fbomanager.platform.api.FixedPrecisionQuantity;
import com.ecillie.fbomanager.platform.api.IdempotencyKey;
import com.ecillie.fbomanager.platform.api.IdempotentCommandExecutor;
import com.ecillie.fbomanager.platform.api.IdempotentResult;
import com.ecillie.fbomanager.platform.api.OperationalStatus;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceRequest;
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceRequestStatus;
import com.ecillie.fbomanager.services.api.ServiceModels.ServiceType;
import com.ecillie.fbomanager.services.api.ServiceRepository;
import com.ecillie.fbomanager.services.api.ServiceRequestService.CreateServiceRequest;
import com.ecillie.fbomanager.services.api.ServiceTaskRequested;
import com.ecillie.fbomanager.services.api.ServiceWorkflow;
import com.ecillie.fbomanager.services.internal.application.DefaultServiceRequestService;
import com.ecillie.fbomanager.tasks.api.TaskModels.AirportTask;
import com.ecillie.fbomanager.tasks.api.TaskModels.TaskStatus;
import com.ecillie.fbomanager.tasks.api.TaskRepository;
import com.ecillie.fbomanager.tasks.api.TaskService.DispatchTask;
import com.ecillie.fbomanager.tasks.internal.application.DefaultTaskService;
import com.ecillie.fbomanager.visits.api.VisitModels.AircraftVisit;
import com.ecillie.fbomanager.visits.api.VisitModels.VisitStatus;
import com.ecillie.fbomanager.visits.api.VisitRepository;
import com.ecillie.fbomanager.visits.api.VisitResourceAccess;
import com.ecillie.fbomanager.visits.api.VisitService.ArriveVisit;
import com.ecillie.fbomanager.visits.internal.application.DefaultVisitService;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerShift;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerShiftStatus;
import com.ecillie.fbomanager.workforce.api.WorkforceRepository;
import com.ecillie.fbomanager.workforce.api.WorkforceResourceAccess;
import com.ecillie.fbomanager.workforce.api.WorkforceService.ShiftTransition;
import com.ecillie.fbomanager.workforce.internal.application.DefaultWorkforceService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

class WorkflowApplicationServiceTests {

	private static final IdempotencyKey KEY = new IdempotencyKey("workflow-unit-key-0001");
	private static final Instant NOW = Instant.parse("2026-08-13T18:00:00Z");
	private final IdempotentCommandExecutor idempotency = new DirectExecutor();

	@Test
	void visitArrivalLocksTheSpotBeforeTheVisitAndRejectsInvalidSourceState() {
		VisitRepository repository = mock(VisitRepository.class);
		ParkingResourceAccess parking = mock(ParkingResourceAccess.class);
		var service = new DefaultVisitService(repository,
				mock(com.ecillie.fbomanager.aircraft.api.AircraftResourceAccess.class), parking, this.idempotency);
		ParkingSpot spot = new ParkingSpot("A1", "RAMP", "A1", OperationalStatus.AVAILABLE, null, null);
		AircraftVisit inbound = visit(10L, VisitStatus.INBOUND);
		when(parking.lockAvailableSpot("A1")).thenReturn(spot);
		when(repository.lock(10L)).thenReturn(Optional.of(inbound));
		when(repository.findOperationalDetails(any(), any()))
				.thenReturn(new RepositoryPage<>(java.util.List.of(), 0, 1, 0));
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AircraftVisit arrived = service.arrive(new ArriveVisit(10L, "A1", NOW), actor(Capability.VISITS_WRITE), KEY)
				.value();

		assertThat(arrived.status()).isEqualTo(VisitStatus.ON_RAMP);
		InOrder locks = inOrder(parking, repository);
		locks.verify(parking).lockAvailableSpot("A1");
		locks.verify(repository).lock(10L);

		when(repository.lock(10L)).thenReturn(Optional.of(visit(10L, VisitStatus.EXPECTED)));
		assertThatThrownBy(() -> service.arrive(new ArriveVisit(10L, "A1", NOW), actor(Capability.VISITS_WRITE),
				new IdempotencyKey("workflow-unit-key-0002"))).isInstanceOf(DomainConflictException.class)
				.hasMessageContaining("transition");
	}

	@Test
	void serviceCreationValidatesTheVisitAndPublishesLinkedTaskInsideTheCommand() {
		ServiceRepository repository = mock(ServiceRepository.class);
		VisitResourceAccess visits = mock(VisitResourceAccess.class);
		ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
		AirportTime time = mock(AirportTime.class);
		var service = new DefaultServiceRequestService(repository, visits, this.idempotency, events, time);
		when(repository.findType("GPU"))
				.thenReturn(Optional.of(new ServiceType("GPU", "GPU", false, null, true, null)));
		when(repository.saveRequest(any())).thenAnswer(invocation -> {
			ServiceRequest value = invocation.getArgument(0);
			return new ServiceRequest(20L, value.aircraftVisitId(), value.serviceTypeCode(), value.fuelTypeCode(),
					value.status(), value.requestedQuantity(), value.quantityUnit(), value.notes(), value.completedAt(),
					null);
		});

		ServiceRequest created = service
				.create(new CreateServiceRequest(10L, "GPU", null, null, null, "Connect power", NOW.plusSeconds(600)),
						actor(Capability.SERVICES_WRITE), KEY)
				.value();

		assertThat(created.status()).isEqualTo(ServiceRequestStatus.REQUESTED);
		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		verify(events).publishEvent(event.capture());
		assertThat(event.getValue()).isEqualTo(new ServiceTaskRequested(20L, 10L, "GPU", NOW.plusSeconds(600)));
	}

	@Test
	void serviceTransitionsRejectAnInvalidSourceState() {
		ServiceRepository repository = mock(ServiceRepository.class);
		var service = new DefaultServiceRequestService(repository, mock(VisitResourceAccess.class), this.idempotency,
				mock(ApplicationEventPublisher.class), mock(AirportTime.class));
		ServiceRequest completed = new ServiceRequest(20L, 10L, "GPU", null, ServiceRequestStatus.COMPLETED, null, null,
				null, NOW, null);
		when(repository.lockRequest(20L)).thenReturn(Optional.of(completed));

		assertThatThrownBy(() -> service.start(20L, actor(Capability.SERVICES_WRITE), KEY))
				.isInstanceOf(DomainConflictException.class).hasMessageContaining("transition");
		verify(repository, never()).saveRequest(any());
	}

	@Test
	void taskDispatchClaimsWorkerThenVehicleThenTaskAndStartsLinkedService() {
		TaskRepository repository = mock(TaskRepository.class);
		WorkforceResourceAccess workforce = mock(WorkforceResourceAccess.class);
		FleetResourceAccess fleet = mock(FleetResourceAccess.class);
		ServiceWorkflow services = mock(ServiceWorkflow.class);
		var service = new DefaultTaskService(repository, workforce, fleet, services, this.idempotency);
		AirportTask pending = new AirportTask(30L, 10L, 20L, null, null, "GPU", null, TaskStatus.PENDING, null, null,
				null, null);
		when(repository.lockForAssignment(30L)).thenReturn(Optional.of(pending));
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AirportTask started = service
				.dispatchAndStart(new DispatchTask(30L, 40L, "GPU_1", NOW), actor(Capability.TASKS_WRITE), KEY).value();

		assertThat(started.status()).isEqualTo(TaskStatus.IN_PROGRESS);
		assertThat(started.assignedWorkerId()).isEqualTo(40L);
		InOrder order = inOrder(workforce, fleet, repository, services);
		order.verify(workforce).lockAssignableWorker(40L);
		order.verify(fleet).lockAssignableVehicle("GPU_1");
		order.verify(repository).lockForAssignment(30L);
		order.verify(services).startForTask(20L);
		order.verify(repository).save(any());
	}

	@Test
	void fuelTransferWritesPairedOppositeLedgerEntriesWithoutTreatingCapacityAsAHardLimit() {
		FuelRepository repository = mock(FuelRepository.class);
		FleetResourceAccess fleet = mock(FleetResourceAccess.class);
		WorkforceResourceAccess workforce = mock(WorkforceResourceAccess.class);
		var service = new DefaultFuelInventoryService(repository, fleet, workforce, mock(ServiceWorkflow.class),
				this.idempotency);
		TankBalance tank = new TankBalance("Main Tank", "JET_A", quantity("1000.000"), "US_GALLON", quantity("10.000"),
				quantity("990.000"));
		TruckBalance truck = new TruckBalance("TRUCK_1", "JET_A", quantity("500.000"), "US_GALLON", quantity("490.000"),
				quantity("10.000"));
		when(repository.lockTankBalance("Main Tank")).thenReturn(Optional.of(tank));
		when(repository.findTank("Main Tank")).thenReturn(
				Optional.of(new FuelTank("Main Tank", "JET_A", quantity("1000.000"), "US_GALLON", null, true, null)));
		when(repository.lockTruckBalance("TRUCK_1")).thenReturn(Optional.of(truck));
		when(repository.nextTransferGroupId()).thenReturn(90L);
		when(repository.findTankBalance("Main Tank")).thenReturn(Optional.of(tank));
		when(repository.findTruckBalance("TRUCK_1")).thenReturn(Optional.of(truck));
		AtomicLong ids = new AtomicLong(100);
		when(repository.append(any())).thenAnswer(invocation -> {
			FuelLedgerEntry entry = invocation.getArgument(0);
			return new FuelLedgerEntry(ids.incrementAndGet(), entry.fuelTypeCode(), entry.fuelTankName(),
					entry.fuelTruckIdentifier(), entry.serviceRequestId(), entry.recordedByWorkerId(),
					entry.transactionType(), entry.quantityDelta(), entry.quantityUnit(), entry.transferGroupId(),
					entry.occurredAt(), entry.notes(), NOW);
		});

		var result = service
				.transfer(new Transfer("Main Tank", "TRUCK_1", quantity("25.000"), "US_GALLON", 40L, NOW, "Load truck"),
						actor(Capability.FUEL_WRITE), KEY)
				.value();

		assertThat(result.tankEntry().quantityDelta()).isEqualTo(quantity("-25.000"));
		assertThat(result.truckEntry().quantityDelta()).isEqualTo(quantity("25.000"));
		assertThat(result.tankEntry().transferGroupId()).isEqualTo(90L);
		assertThat(result.truckEntry().transferGroupId()).isEqualTo(90L);
		verify(repository, never()).saveTank(any());
	}

	@Test
	void shiftTransitionsRejectAnInvalidSourceState() {
		WorkforceRepository repository = mock(WorkforceRepository.class);
		var service = new DefaultWorkforceService(repository, this.idempotency);
		WorkerShift scheduled = new WorkerShift(50L, 40L, NOW.minusSeconds(3600), NOW.plusSeconds(3600), null, null,
				WorkerShiftStatus.SCHEDULED, null, null);
		when(repository.lockShift(50L)).thenReturn(Optional.of(scheduled));

		assertThatThrownBy(
				() -> service.completeShift(new ShiftTransition(50L, NOW), actor(Capability.WORKFORCE_WRITE), KEY))
				.isInstanceOf(DomainConflictException.class).hasMessageContaining("transition");
		verify(repository, never()).saveShift(any());
	}

	private static AircraftVisit visit(long id, VisitStatus status) {
		return new AircraftVisit(id, "N123AB", null, status, NOW.minusSeconds(600), null, NOW.plusSeconds(3600), null,
				null, null);
	}

	private static FixedPrecisionQuantity quantity(String value) {
		return new FixedPrecisionQuantity(value);
	}

	private static ActorContext actor(Capability capability) {
		return new ActorContext("workflow-unit-actor", Set.of(capability));
	}

	private static final class DirectExecutor implements IdempotentCommandExecutor {

		@Override
		public <T> IdempotentResult<T> execute(String operationId, IdempotencyKey key, Object command,
				ActorContext actor, int responseStatus, Duration retention, Class<T> resultType, Supplier<T> action) {
			return new IdempotentResult<>(action.get(), responseStatus, false);
		}

		@Override
		public <T> IdempotentResult<T> executeInventory(String operationId, IdempotencyKey key, Object command,
				ActorContext actor, int responseStatus, Class<T> resultType, Function<T, Long> evidenceTransaction,
				Supplier<T> action) {
			T result = action.get();
			assertThat(evidenceTransaction.apply(result)).isNotNull();
			return new IdempotentResult<>(result, responseStatus, false);
		}
	}
}
