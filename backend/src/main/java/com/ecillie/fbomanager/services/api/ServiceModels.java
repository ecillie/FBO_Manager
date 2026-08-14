package com.ecillie.fbomanager.services.api;

import com.ecillie.fbomanager.platform.api.AuditMetadata;
import com.ecillie.fbomanager.platform.api.FixedPrecisionQuantity;
import com.ecillie.fbomanager.platform.api.NaturalKey;
import java.time.Instant;
import java.util.List;

public final class ServiceModels {

	private ServiceModels() {
	}

	public enum ServiceRequestStatus {
		REQUESTED, IN_PROGRESS, COMPLETED, CANCELLED
	}

	public record ServiceType(String code, String name, boolean fuelService, String defaultUnit, boolean active,
			AuditMetadata audit) {
		public ServiceType {
			code = NaturalKey.code(code);
			name = NaturalKey.name(name);
			defaultUnit = NaturalKey.optionalCode(defaultUnit);
		}
	}

	public record ServiceRequest(Long serviceRequestId, long aircraftVisitId, String serviceTypeCode,
			String fuelTypeCode, ServiceRequestStatus status, FixedPrecisionQuantity requestedQuantity,
			String quantityUnit, String notes, Instant completedAt, AuditMetadata audit) {
		public ServiceRequest {
			serviceTypeCode = NaturalKey.code(serviceTypeCode);
			fuelTypeCode = NaturalKey.optionalCode(fuelTypeCode);
			quantityUnit = NaturalKey.optionalCode(quantityUnit);
			if (status == null) {
				throw new IllegalArgumentException("status must not be null");
			}
		}
	}

	public record ServiceRequestFilter(Long aircraftVisitId, List<ServiceRequestStatus> statuses,
			String serviceTypeCode) {
		public ServiceRequestFilter {
			statuses = statuses == null ? List.of() : List.copyOf(statuses);
			serviceTypeCode = NaturalKey.optionalCode(serviceTypeCode);
		}
	}
}
