package com.ecillie.fbomanager.platform.internal.reference;

/**
 * Application boundary used by the HTTP convention-reference fixture.
 */
public interface ConventionReferenceUseCase {

	ConventionReferenceResult handle(ConventionReferenceCommand command);
}
