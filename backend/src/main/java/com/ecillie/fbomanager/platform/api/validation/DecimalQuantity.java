package com.ecillie.fbomanager.platform.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = DecimalQuantityValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface DecimalQuantity {

	String message() default "Must be a positive decimal with three fractional digits.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
