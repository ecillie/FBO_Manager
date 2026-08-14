package com.ecillie.fbomanager.workforce.api;

import com.ecillie.fbomanager.platform.api.AuditMetadata;
import com.ecillie.fbomanager.platform.api.NaturalKey;
import java.time.Instant;
import java.util.List;

public final class WorkforceModels {

	private WorkforceModels() {
	}

	public enum WorkerShiftStatus {
		SCHEDULED, IN_PROGRESS, COMPLETED, ABSENT, CANCELLED
	}

	public record Role(String name, String description, AuditMetadata audit) {
		public Role {
			name = NaturalKey.name(name);
		}
	}

	public record Worker(Long workerId, String roleName, String firstName, String lastName, String phone, String email,
			boolean active, AuditMetadata audit) {
		public Worker {
			roleName = NaturalKey.name(roleName);
			firstName = NaturalKey.name(firstName);
			lastName = NaturalKey.name(lastName);
			phone = NaturalKey.optionalText(phone);
			email = NaturalKey.email(email);
		}
	}

	public record WorkerShift(Long shiftId, long workerId, Instant scheduledStartAt, Instant scheduledEndAt,
			Instant actualStartAt, Instant actualEndAt, WorkerShiftStatus status, String notes, AuditMetadata audit) {
		public WorkerShift {
			if (scheduledStartAt == null || scheduledEndAt == null || status == null) {
				throw new IllegalArgumentException("schedule and status are required");
			}
		}
	}

	public record WorkerStatus(long workerId, String firstName, String lastName, String roleName, boolean atWork,
			Long currentTaskId, String currentTaskTitle) {
	}

	public record WorkerFilter(Boolean active, String roleName, List<WorkerShiftStatus> shiftStatuses,
			String nameContains) {
		public WorkerFilter {
			roleName = NaturalKey.optionalName(roleName);
			shiftStatuses = shiftStatuses == null ? List.of() : List.copyOf(shiftStatuses);
			nameContains = nameContains == null ? null : NaturalKey.text(nameContains);
		}
	}
}
