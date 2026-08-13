package com.ecillie.fbomanager.platform.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.regex.Pattern;

final class UtcTimestampValidator implements ConstraintValidator<UtcTimestamp, String> {

	private static final Pattern WIRE_FORMAT = Pattern
			.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,6})?Z");

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || !WIRE_FORMAT.matcher(value).matches()) {
			return value == null;
		}
		try {
			Instant.parse(value);
			return true;
		} catch (DateTimeException exception) {
			return false;
		}
	}
}
