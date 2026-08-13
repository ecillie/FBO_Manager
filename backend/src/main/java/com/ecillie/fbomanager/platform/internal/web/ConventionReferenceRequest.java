package com.ecillie.fbomanager.platform.internal.web;

import com.ecillie.fbomanager.platform.api.validation.ClosedEnum;
import com.ecillie.fbomanager.platform.api.validation.DecimalQuantity;
import com.ecillie.fbomanager.platform.api.validation.GeneratedIdentifier;
import com.ecillie.fbomanager.platform.api.validation.UtcTimestamp;
import com.ecillie.fbomanager.platform.internal.reference.QuantityUnit;
import com.ecillie.fbomanager.platform.internal.reference.ReferenceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

record ConventionReferenceRequest(
		@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,31}", message = "Must be an upper-snake-case code.") String referenceCode,
		@NotBlank @GeneratedIdentifier String sourceId, @NotBlank @ClosedEnum(ReferenceStatus.class) String status,
		@NotBlank @DecimalQuantity String requestedQuantity,
		@NotBlank @ClosedEnum(QuantityUnit.class) String quantityUnit, @NotBlank @UtcTimestamp String occurredAt) {
}
