package com.ecillie.fbomanager.operations.api;

import com.ecillie.fbomanager.fleet.api.FleetModels.VehicleStatus;
import com.ecillie.fbomanager.fuel.api.FuelModels.TankBalance;
import com.ecillie.fbomanager.fuel.api.FuelModels.TruckBalance;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingSpot;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.tasks.api.TaskModels.AirportTask;
import com.ecillie.fbomanager.visits.api.VisitModels.VisitDetail;
import com.ecillie.fbomanager.workforce.api.WorkforceModels.WorkerStatus;
import java.time.Instant;
import java.time.LocalDate;

public final class OperationsModels {

	private OperationsModels() {
	}

	public record OperationsDashboard(LocalDate operatingDate, Instant asOf, RepositoryPage<VisitDetail> rampBoard,
			RepositoryPage<ParkingSpot> parking, RepositoryPage<AirportTask> workQueue,
			RepositoryPage<WorkerStatus> workers, RepositoryPage<VehicleStatus> vehicles,
			RepositoryPage<TankBalance> tanks, RepositoryPage<TruckBalance> trucks) {
	}
}
