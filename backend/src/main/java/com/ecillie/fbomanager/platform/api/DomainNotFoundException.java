package com.ecillie.fbomanager.platform.api;

import java.util.Map;

public final class DomainNotFoundException extends DomainException {

	public DomainNotFoundException(String code, String safeMessage) {
		this(code, safeMessage, Map.of());
	}

	public DomainNotFoundException(String code, String safeMessage, Map<String, String> details) {
		super(Category.NOT_FOUND, code, safeMessage, details, false, null, null);
	}
}
