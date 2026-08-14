package com.ecillie.fbomanager.operations.internal.application;

import com.ecillie.fbomanager.fleet.api.FleetModels.VehicleFilter;
import com.ecillie.fbomanager.fleet.api.FleetRepository;
import com.ecillie.fbomanager.fuel.api.FuelRepository;
import com.ecillie.fbomanager.operations.api.OperationsModels.OperationsDashboard;
import com.ecillie.fbomanager.operations.api.OperationsService;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingSpotFilter;
import com.ecillie.fbomanager.parking.api.ParkingRepository;
import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.AirportTime;
import com.ecillie.fbomanager.platform.api.Capability;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest.Direction;
import com.ecillie.fbomanager.tasks.api.TaskModels.TaskFilter;
import com.ecillie.fbomanager.tasks.api.TaskModels.TaskStatus;
import com.ecillie.fbomanager.tasks.api.TaskRepository;
import com.ecillie.fbomanager.visits.api.VisitModels.VisitFilter;
import com.ecillie.fbomanager.visits.api.VisitModels.VisitStatus;
import com.ecillie.fbomanager.visits.api.VisitRepository;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerFilter;
import com.ecillie.fbomanager.workforce.api.WorkforceRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultOperationsService implements OperationsService {

	private final VisitRepository visits;
	private final ParkingRepository parking;
	private final TaskRepository tasks;
	private final WorkforceRepository workforce;
	private final FleetRepository fleet;
	private final FuelRepository fuel;
	private final AirportTime time;

	public DefaultOperationsService(VisitRepository visits, ParkingRepository parking, TaskRepository tasks,
			WorkforceRepository workforce, FleetRepository fleet, FuelRepository fuel, AirportTime time) {
		this.visits = visits;
		this.parking = parking;
		this.tasks = tasks;
		this.workforce = workforce;
		this.fleet = fleet;
		this.fuel = fuel;
		this.time = time;
	}

	@Override
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public OperationsDashboard dashboard(LocalDate operatingDate, ActorContext actor) {
		actor.require(Capability.OPERATIONS_READ);
		LocalDate date = operatingDate == null ? this.time.operatingDate(this.time.now()) : operatingDate;
		Instant from = date.atStartOfDay(this.time.zoneId()).toInstant();
		Instant before = date.plusDays(1).atStartOfDay(this.time.zoneId()).toInstant();
		return new OperationsDashboard(date, this.time.now(),
				this.visits.findOperationalDetails(
						new VisitFilter(List.of(VisitStatus.EXPECTED, VisitStatus.INBOUND, VisitStatus.ON_RAMP), null,
								null, from, before),
						page("estimatedArrivalAt")),
				this.parking.findSpots(new ParkingSpotFilter(null, null, null), page("spotCode")),
				this.tasks.findTasks(new TaskFilter(null, null, null, null,
						List.of(TaskStatus.PENDING, TaskStatus.IN_PROGRESS), null, null), page("dueAt")),
				this.workforce.findCurrentStatuses(new WorkerFilter(true, null, List.of(), null), page("lastName")),
				this.fleet.findCurrentStatuses(new VehicleFilter(true, null, null), page("identifier")),
				this.fuel.findTankBalances(page("name")), this.fuel.findTruckBalances(page("identifier")));
	}

	private static RepositoryPageRequest page(String sort) {
		return new RepositoryPageRequest(0, RepositoryPageRequest.MAXIMUM_LIMIT, sort, Direction.ASC);
	}
}
