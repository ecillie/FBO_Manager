package com.ecillie.fbomanager.platform.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

final class ClosedEnumValidator implements ConstraintValidator<ClosedEnum, String> {

	private Set<String> values;

	@Override
	public void initialize(ClosedEnum constraint) {
		this.values = Arrays.stream(constraint.value().getEnumConstants()).map(Enum::name)
				.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || this.values.contains(value);
	}
}
