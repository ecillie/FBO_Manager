package com.ecillie.fbomanager.platform.api;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.regex.Pattern;

public record IdempotencyKey(@JsonValue String value) {

	private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9._:-]{16,128}");

	public IdempotencyKey {
		Objects.requireNonNull(value, "value");
		if (!VALID_KEY.matcher(value).matches()) {
			throw new IllegalArgumentException("Invalid idempotency key");
		}
	}
}
