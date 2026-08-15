package com.ecillie.fbomanager.platform.api;

import java.util.Objects;
import java.util.Set;

/**
 * Authenticated identity and capabilities resolved before a workflow begins.
 */
public record ActorContext(String subject, Set<Capability> capabilities) {

	public ActorContext {
		Objects.requireNonNull(subject, "subject");
		if (subject.isBlank() || subject.length() > 200) {
			throw new IllegalArgumentException("subject must be a bounded nonblank value");
		}
		capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
	}

	public void require(Capability capability) {
		if (!this.capabilities.contains(Objects.requireNonNull(capability, "capability"))) {
			throw new DomainAuthorizationException("CAPABILITY_REQUIRED", "The requested operation is not permitted.",
					java.util.Map.of("capability", capability.name()));
		}
	}
}
