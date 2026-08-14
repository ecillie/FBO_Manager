package com.ecillie.fbomanager.tasks.api;

import com.ecillie.fbomanager.platform.api.AuditMetadata;
import com.ecillie.fbomanager.platform.api.NaturalKey;
import java.time.Instant;
import java.util.List;

public final class TaskModels {

	private TaskModels() {
	}

	public enum TaskStatus {
		PENDING, IN_PROGRESS, COMPLETED, CANCELLED
	}

	public record AirportTask(Long taskId, Long aircraftVisitId, Long serviceRequestId, String serviceVehicleIdentifier,
			Long assignedWorkerId, String title, String description, TaskStatus status, Instant dueAt,
			Instant startedAt, Instant completedAt, AuditMetadata audit) {
		public AirportTask {
			serviceVehicleIdentifier = NaturalKey.optionalCode(serviceVehicleIdentifier);
			title = NaturalKey.text(title);
			if (status == null) {
				throw new IllegalArgumentException("status must not be null");
			}
		}
	}

	public record TaskFilter(Long aircraftVisitId, Long serviceRequestId, Long assignedWorkerId,
			String serviceVehicleIdentifier, List<TaskStatus> statuses, Instant dueFrom, Instant dueBefore) {
		public TaskFilter {
			serviceVehicleIdentifier = NaturalKey.optionalCode(serviceVehicleIdentifier);
			statuses = statuses == null ? List.of() : List.copyOf(statuses);
			if (dueFrom != null && dueBefore != null && !dueFrom.isBefore(dueBefore)) {
				throw new IllegalArgumentException("dueFrom must be before dueBefore");
			}
		}
	}
}
