package com.ecillie.fbomanager.platform.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import java.util.regex.Pattern;

final class DecimalQuantityValidator implements ConstraintValidator<DecimalQuantity, String> {

	private static final Pattern WIRE_FORMAT = Pattern.compile("[0-9]{1,11}\\.[0-9]{3}");

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || (WIRE_FORMAT.matcher(value).matches() && new BigDecimal(value).signum() > 0);
	}
}
