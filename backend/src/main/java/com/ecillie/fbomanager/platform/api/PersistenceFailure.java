package com.ecillie.fbomanager.platform.api;

import java.util.Objects;

/** Safe, stable persistence failure that never exposes driver messages. */
public final class PersistenceFailure extends RuntimeException {

	private final Kind kind;
	private final String constraint;

	public PersistenceFailure(Kind kind, String constraint, Throwable cause) {
		super("Persistence operation failed: " + Objects.requireNonNull(kind), cause);
		this.kind = kind;
		this.constraint = constraint;
	}

	public Kind kind() {
		return this.kind;
	}

	public String constraint() {
		return this.constraint;
	}

	public enum Kind {
		UNIQUE_CONFLICT, FOREIGN_KEY_CONFLICT, CHECK_VIOLATION, CONCURRENCY_CONFLICT, DEPENDENCY_UNAVAILABLE, UNEXPECTED
	}
}
