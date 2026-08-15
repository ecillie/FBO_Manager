package com.ecillie.fbomanager.visits.api;

import com.ecillie.fbomanager.platform.api.AuditMetadata;
import com.ecillie.fbomanager.platform.api.FixedPrecisionQuantity;
import com.ecillie.fbomanager.platform.api.NaturalKey;
import java.time.Instant;
import java.util.List;

public final class VisitModels {

	private VisitModels() {
	}

	public enum VisitStatus {
		EXPECTED, INBOUND, ON_RAMP, DEPARTED, CANCELLED
	}

	public record AircraftVisit(Long visitId, String tailNumber, String parkingSpotCode, VisitStatus status,
			Instant estimatedArrivalAt, Instant actualArrivalAt, Instant estimatedDepartureAt,
			Instant actualDepartureAt, String notes, AuditMetadata audit) {
		public AircraftVisit {
			tailNumber = NaturalKey.identifier(tailNumber);
			parkingSpotCode = NaturalKey.optionalCode(parkingSpotCode);
			if (status == null) {
				throw new IllegalArgumentException("status must not be null");
			}
		}
	}

	public record VisitFilter(List<VisitStatus> statuses, String tailNumber, String parkingSpotCode,
			Instant arrivalFrom, Instant arrivalBefore) {
		public VisitFilter {
			statuses = statuses == null ? List.of() : List.copyOf(statuses);
			tailNumber = NaturalKey.optionalCode(tailNumber);
			parkingSpotCode = NaturalKey.optionalCode(parkingSpotCode);
			if (arrivalFrom != null && arrivalBefore != null && !arrivalFrom.isBefore(arrivalBefore)) {
				throw new IllegalArgumentException("arrivalFrom must be before arrivalBefore");
			}
		}
	}

	/**
	 * One-query operational projection used for visit detail and ramp-board reads.
	 */
	public record VisitDetail(AircraftVisit visit, String manufacturerName, String modelName, String categoryCode,
			String operationTypeCode, String fuelTypeCode, String parkingAreaCode, String parkingSpotName) {
	}

	public record ServiceSummary(long serviceRequestId, String serviceTypeCode, String status, String fuelTypeCode,
			FixedPrecisionQuantity requestedQuantity, String quantityUnit) {
	}

	public record TaskSummary(long taskId, String title, String status, Long assignedWorkerId,
			String serviceVehicleIdentifier) {
	}

	/** Complete operational detail materialized by one joined repository query. */
	public record OperationalVisitDetail(VisitDetail visit, List<ServiceSummary> services, List<TaskSummary> tasks) {
		public OperationalVisitDetail {
			services = List.copyOf(services);
			tasks = List.copyOf(tasks);
		}
	}
}
