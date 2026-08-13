package com.ecillie.fbomanager.platform.internal.web;

import com.ecillie.fbomanager.platform.api.FixedPrecisionQuantity;
import com.ecillie.fbomanager.platform.api.GeneratedId;
import com.ecillie.fbomanager.platform.internal.reference.QuantityUnit;
import com.ecillie.fbomanager.platform.internal.reference.ReferenceStatus;
import java.time.Instant;

record ConventionReferenceResponse(GeneratedId referenceId, String referenceCode, ReferenceStatus status,
		FixedPrecisionQuantity requestedQuantity, QuantityUnit quantityUnit, Instant occurredAt) {
}
