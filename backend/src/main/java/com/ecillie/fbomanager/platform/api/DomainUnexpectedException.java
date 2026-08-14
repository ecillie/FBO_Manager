package com.ecillie.fbomanager.platform.api;

import java.util.Map;

public final class DomainUnexpectedException extends DomainException {

	public DomainUnexpectedException(String code, String safeMessage, Throwable cause) {
		super(Category.UNEXPECTED, code, safeMessage, Map.of(), false, null, cause);
	}
}
