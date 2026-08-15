package com.ecillie.fbomanager.platform.api;

import java.util.Objects;
import java.util.Set;

/**
 * Framework-neutral, bounded repository pagination with an allowlisted sort.
 */
public record RepositoryPageRequest(int offset, int limit, String sortBy, Direction direction) {

	public static final int MAXIMUM_LIMIT = 100;

	public RepositoryPageRequest {
		if (offset < 0) {
			throw new IllegalArgumentException("offset must not be negative");
		}
		if (limit < 1 || limit > MAXIMUM_LIMIT) {
			throw new IllegalArgumentException("limit must be between 1 and " + MAXIMUM_LIMIT);
		}
		sortBy = NaturalKey.text(sortBy);
		direction = Objects.requireNonNull(direction, "direction must not be null");
	}

	public String requireAllowedSort(Set<String> allowedSorts) {
		if (!allowedSorts.contains(this.sortBy)) {
			throw new IllegalArgumentException("unsupported sort: " + this.sortBy);
		}
		return this.sortBy;
	}

	public enum Direction {
		ASC, DESC
	}
}
