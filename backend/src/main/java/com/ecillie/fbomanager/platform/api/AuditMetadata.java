package com.ecillie.fbomanager.platform.api;

import java.time.Instant;
import java.util.Objects;

/** Database-managed creation and last-update timestamps. */
public record AuditMetadata(Instant createdAt, Instant updatedAt) {

	public AuditMetadata {
		Objects.requireNonNull(createdAt, "createdAt must not be null");
		Objects.requireNonNull(updatedAt, "updatedAt must not be null");
	}
}
