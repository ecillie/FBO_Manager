package com.ecillie.fbomanager.platform.api;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Safe, framework-neutral failure raised by an application workflow. */
public abstract sealed class DomainException extends RuntimeException permits DomainAuthorizationException,
		DomainConflictException, DomainNotFoundException, DomainUnexpectedException, DomainValidationException {

	private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");
	private final Category category;
	private final String code;
	private final Map<String, String> details;
	private final boolean retryable;
	private final Duration retryAfter;

	protected DomainException(Category category, String code, String safeMessage, Map<String, String> details,
			boolean retryable, Duration retryAfter, Throwable cause) {
		super(requireMessage(safeMessage), cause);
		this.category = Objects.requireNonNull(category, "category");
		if (!CODE.matcher(Objects.requireNonNull(code, "code")).matches()) {
			throw new IllegalArgumentException("Domain error code must be upper snake case");
		}
		this.code = code;
		this.details = Map.copyOf(Objects.requireNonNull(details, "details"));
		this.retryable = retryable;
		this.retryAfter = retryAfter;
	}

	public Category category() {
		return this.category;
	}

	public String code() {
		return this.code;
	}

	public Map<String, String> details() {
		return this.details;
	}

	public boolean retryable() {
		return this.retryable;
	}

	public Duration retryAfter() {
		return this.retryAfter;
	}

	private static String requireMessage(String message) {
		Objects.requireNonNull(message, "safeMessage");
		if (message.isBlank() || message.length() > 256 || message.indexOf('\n') >= 0 || message.indexOf('\r') >= 0) {
			throw new IllegalArgumentException("Safe messages must be one nonblank line of at most 256 characters");
		}
		return message;
	}

	public enum Category {
		VALIDATION, NOT_FOUND, CONFLICT, AUTHORIZATION, UNEXPECTED
	}
}
