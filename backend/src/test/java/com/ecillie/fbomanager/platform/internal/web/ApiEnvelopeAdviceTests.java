package com.ecillie.fbomanager.platform.internal.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;

class ApiEnvelopeAdviceTests {

	@Test
	void preventsRawSpringDataPageSerialization() {
		ApiEnvelopeAdvice advice = new ApiEnvelopeAdvice();

		assertThatThrownBy(() -> advice.beforeBodyWrite(new PageImpl<>(List.of("record")), null,
				MediaType.APPLICATION_JSON, null, null, null)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ApiCollectionResponse");
	}
}
