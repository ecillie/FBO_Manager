package com.ecillie.fbomanager.platform.api;

import java.util.Map;

public final class DomainValidationException extends DomainException {

	public DomainValidationException(String code, String safeMessage) {
		this(code, safeMessage, Map.of());
	}

	public DomainValidationException(String code, String safeMessage, Map<String, String> details) {
		super(Category.VALIDATION, code, safeMessage, details, false, null, null);
	}
}
