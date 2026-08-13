package com.ecillie.fbomanager.platform.api;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

public final class ApiException extends RuntimeException {

	private static final Pattern APPLICATION_CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");
	private static final Pattern DETAIL_KEY = Pattern.compile("[a-z][A-Za-z0-9]{0,63}");

	private final HttpStatus status;
	private final String code;
	private final boolean retryable;
	private final Map<String, String> details;
	private final Duration retryAfter;

	public ApiException(HttpStatus status, String code, String safeMessage) {
		this(status, code, safeMessage, false, Map.of(), null);
	}

	public ApiException(HttpStatus status, String code, String safeMessage, boolean retryable,
			Map<String, String> details, Duration retryAfter) {
		super(requireSafeMessage(safeMessage));
		this.status = Objects.requireNonNull(status, "status");
		if (!APPLICATION_CODE.matcher(Objects.requireNonNull(code, "code")).matches()) {
			throw new IllegalArgumentException("Application error code must be upper snake case");
		}
		this.code = code;
		this.retryable = retryable;
		this.details = requireSafeDetails(details);
		this.retryAfter = retryAfter;
	}

	public HttpStatus status() {
		return this.status;
	}

	public String code() {
		return this.code;
	}

	public boolean retryable() {
		return this.retryable;
	}

	public Map<String, String> details() {
		return this.details;
	}

	public Duration retryAfter() {
		return this.retryAfter;
	}

	private static String requireSafeMessage(String message) {
		Objects.requireNonNull(message, "safeMessage");
		if (message.isBlank() || message.length() > 256 || message.indexOf('\n') >= 0 || message.indexOf('\r') >= 0) {
			throw new IllegalArgumentException("Safe messages must be one nonblank line of at most 256 characters");
		}
		return message;
	}

	private static Map<String, String> requireSafeDetails(Map<String, String> details) {
		Objects.requireNonNull(details, "details");
		details.forEach((key, value) -> {
			if (!DETAIL_KEY.matcher(key).matches() || value == null || value.isBlank() || value.length() > 128
					|| value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
				throw new IllegalArgumentException("Error details must use bounded safe scalar values");
			}
		});
		return Map.copyOf(details);
	}
}
