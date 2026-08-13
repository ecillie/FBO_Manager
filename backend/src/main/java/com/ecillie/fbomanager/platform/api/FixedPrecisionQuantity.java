package com.ecillie.fbomanager.platform.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A PostgreSQL {@code NUMERIC(14,3)} value represented as an exact JSON string.
 */
public record FixedPrecisionQuantity(@JsonValue String value) {

	private static final Pattern DECIMAL_QUANTITY = Pattern.compile("-?[0-9]{1,11}\\.[0-9]{3}");

	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public FixedPrecisionQuantity {
		Objects.requireNonNull(value, "value");
		if (!DECIMAL_QUANTITY.matcher(value).matches()) {
			throw new IllegalArgumentException("Quantities require exactly three fractional digits");
		}
	}

	public static FixedPrecisionQuantity from(BigDecimal value) {
		Objects.requireNonNull(value, "value");
		BigDecimal scaled = value.setScale(3, RoundingMode.UNNECESSARY);
		if (scaled.precision() - scaled.scale() > 11) {
			throw new IllegalArgumentException("Quantity is outside the NUMERIC(14,3) range");
		}
		return new FixedPrecisionQuantity(scaled.toPlainString());
	}

	public BigDecimal toBigDecimal() {
		return new BigDecimal(this.value);
	}
}
