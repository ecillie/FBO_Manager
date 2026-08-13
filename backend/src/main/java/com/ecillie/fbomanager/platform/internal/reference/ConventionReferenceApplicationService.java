package com.ecillie.fbomanager.platform.internal.reference;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * A side-effect-free transport reference; feature services replace it on real
 * resource routes.
 */
@Service
public class ConventionReferenceApplicationService {

	static final long REFERENCE_ID = 9_007_199_254_740_993L;

	private final AtomicLong invocationCount = new AtomicLong();

	public ConventionReferenceResult handle(ConventionReferenceCommand command) {
		this.invocationCount.incrementAndGet();
		return new ConventionReferenceResult(REFERENCE_ID, command.referenceCode(), command.status(),
				command.requestedQuantity(), command.quantityUnit(), command.occurredAt());
	}

	public long invocationCount() {
		return this.invocationCount.get();
	}
}
