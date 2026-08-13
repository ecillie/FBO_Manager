package com.ecillie.fbomanager.platform.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A database-generated positive {@code BIGINT} represented safely on the JSON
 * wire.
 */
public record GeneratedId(@JsonValue String value) {

	private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]{0,18}");

	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public GeneratedId {
		Objects.requireNonNull(value, "value");
		if (!DECIMAL_ID.matcher(value).matches()) {
			throw new IllegalArgumentException("Generated IDs must be positive base-10 strings");
		}
		try {
			Long.parseLong(value);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Generated ID is outside the BIGINT range", exception);
		}
	}

	public static GeneratedId from(long value) {
		if (value <= 0) {
			throw new IllegalArgumentException("Generated IDs must be positive");
		}
		return new GeneratedId(Long.toString(value));
	}
}
