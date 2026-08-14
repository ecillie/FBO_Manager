package com.ecillie.fbomanager.platform.api;

import java.time.Duration;
import java.util.Map;

public final class DomainConflictException extends DomainException {

	public DomainConflictException(String code, String safeMessage) {
		this(code, safeMessage, Map.of(), false, null);
	}

	public DomainConflictException(String code, String safeMessage, Map<String, String> details) {
		this(code, safeMessage, details, false, null);
	}

	public DomainConflictException(String code, String safeMessage, Map<String, String> details, boolean retryable,
			Duration retryAfter) {
		super(Category.CONFLICT, code, safeMessage, details, retryable, retryAfter, null);
	}
}
