package com.ecillie.fbomanager.platform.api;

import java.util.Locale;
import java.util.Objects;

/** Central normalization rules for database natural keys. */
public final class NaturalKey {

	private NaturalKey() {
	}

	public static String code(String value) {
		return text(value).toUpperCase(Locale.ROOT);
	}

	public static String identifier(String value) {
		return code(value);
	}

	public static String name(String value) {
		return text(value);
	}

	public static String email(String value) {
		return value == null ? null : text(value).toLowerCase(Locale.ROOT);
	}

	public static String optionalCode(String value) {
		return value == null ? null : code(value);
	}

	public static String optionalName(String value) {
		return value == null ? null : name(value);
	}

	public static String optionalText(String value) {
		return value == null ? null : text(value);
	}

	public static String text(String value) {
		String normalized = Objects.requireNonNull(value, "natural key must not be null").strip();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("natural key must not be blank");
		}
		return normalized;
	}
}
