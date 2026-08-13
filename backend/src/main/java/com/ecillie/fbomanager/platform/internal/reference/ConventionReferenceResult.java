package com.ecillie.fbomanager.platform.internal.reference;

import java.math.BigDecimal;
import java.time.Instant;

public record ConventionReferenceResult(long referenceId, String referenceCode, ReferenceStatus status,
		BigDecimal requestedQuantity, QuantityUnit quantityUnit, Instant occurredAt) {
}
