package com.ecillie.fbomanager.platform.api;

import java.util.Map;

public final class DomainAuthorizationException extends DomainException {

	public DomainAuthorizationException(String code, String safeMessage, Map<String, String> details) {
		super(Category.AUTHORIZATION, code, safeMessage, details, false, null, null);
	}
}
