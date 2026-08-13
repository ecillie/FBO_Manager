package com.ecillie.fbomanager.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ApiWireTypesTests {

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	@Test
	void serializesDatabaseIdsBeyondJavaScriptPrecisionAsDecimalStrings() throws Exception {
		assertThat(this.objectMapper.writeValueAsString(GeneratedId.from(9_007_199_254_740_993L)))
				.isEqualTo("\"9007199254740993\"");
	}

	@Test
	void serializesFixedPrecisionQuantitiesWithoutFloatingPointOrExponentNotation() throws Exception {
		assertThat(this.objectMapper.writeValueAsString(FixedPrecisionQuantity.from(new BigDecimal("125.000"))))
				.isEqualTo("\"125.000\"");
		assertThat(this.objectMapper.writeValueAsString(FixedPrecisionQuantity.from(new BigDecimal("-0.125"))))
				.isEqualTo("\"-0.125\"");
	}

	@Test
	void rejectsWirePrimitivesThatCannotMatchDatabasePrecision() {
		assertThatThrownBy(() -> new GeneratedId("9999999999999999999")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new FixedPrecisionQuantity("1.2")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> FixedPrecisionQuantity.from(new BigDecimal("1.2345")))
				.isInstanceOf(ArithmeticException.class);
	}
}
