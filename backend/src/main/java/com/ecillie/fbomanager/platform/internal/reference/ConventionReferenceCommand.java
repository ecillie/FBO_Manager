package com.ecillie.fbomanager.platform.internal.reference;

import java.math.BigDecimal;
import java.time.Instant;

public record ConventionReferenceCommand(String referenceCode, long sourceId, ReferenceStatus status,
		BigDecimal requestedQuantity, QuantityUnit quantityUnit, Instant occurredAt) {
}
