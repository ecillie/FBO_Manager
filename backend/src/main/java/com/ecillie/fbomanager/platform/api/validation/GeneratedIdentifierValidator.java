package com.ecillie.fbomanager.platform.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

final class GeneratedIdentifierValidator implements ConstraintValidator<GeneratedIdentifier, String> {

	private static final Pattern WIRE_FORMAT = Pattern.compile("[1-9][0-9]{0,18}");

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || !WIRE_FORMAT.matcher(value).matches()) {
			return value == null;
		}
		try {
			Long.parseLong(value);
			return true;
		} catch (NumberFormatException exception) {
			return false;
		}
	}
}
