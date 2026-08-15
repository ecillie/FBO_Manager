package com.ecillie.fbomanager.platform.api;

import java.time.Duration;
import java.util.Map;

/** Converts persistence categories without exposing SQL or driver text. */
public final class DomainFailures {

	private DomainFailures() {
	}

	public static DomainException from(PersistenceFailure failure, String conflictCode) {
		return switch (failure.kind()) {
			case UNIQUE_CONFLICT ->
				new DomainConflictException(conflictCode, "The requested change conflicts with current state.");
			case FOREIGN_KEY_CONFLICT, CHECK_VIOLATION ->
				new DomainValidationException("DOMAIN_RULE_VIOLATION", "The requested change violates a domain rule.");
			case CONCURRENCY_CONFLICT -> new DomainConflictException("CONCURRENT_MODIFICATION",
					"The resource changed concurrently; retry the operation.", Map.of(), true, Duration.ofSeconds(1));
			case DEPENDENCY_UNAVAILABLE -> new DomainUnexpectedException("DEPENDENCY_UNAVAILABLE",
					"A required dependency is temporarily unavailable.", failure);
			case UNEXPECTED ->
				new DomainUnexpectedException("PERSISTENCE_FAILURE", "The operation could not be completed.", failure);
		};
	}
}
