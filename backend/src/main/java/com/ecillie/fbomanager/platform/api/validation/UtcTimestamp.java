package com.ecillie.fbomanager.platform.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = UtcTimestampValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface UtcTimestamp {

	String message() default "Must be a UTC ISO 8601 timestamp with at most six fractional digits.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
