package com.ecillie.fbomanager.platform.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestCorrelationFilterTests {

	private final RequestCorrelationFilter filter = new RequestCorrelationFilter();
	private final Logger logger = (Logger) LoggerFactory.getLogger(RequestCorrelationFilter.class);
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@BeforeEach
	void attachAppender() {
		this.appender.start();
		this.logger.addAppender(this.appender);
	}

	@AfterEach
	void detachAppenderAndClearMdc() {
		this.logger.detachAppender(this.appender);
		this.appender.stop();
		MDC.clear();
	}

	@Test
	void preservesAValidInboundRequestIdAndClearsItAfterTheRequest() throws Exception {
		MockHttpServletRequest request = request("GET", "/api/v1/visits/42");
		request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "client-request-123");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> requestIdSeenByChain = new AtomicReference<>();

		this.filter.doFilter(request, response,
				(req, res) -> requestIdSeenByChain.set(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)));

		assertThat(requestIdSeenByChain).hasValue("client-request-123");
		assertThat(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)).isEqualTo("client-request-123");
		assertThat(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)).isNull();
	}

	@Test
	void replacesAnUnsafeRequestIdAndLogsOnlyAllowlistedRequestFields() throws Exception {
		MockHttpServletRequest request = request("GET", "/api/v1/visits/secret-visit-id");
		request.setQueryString("token=secret-token");
		request.addHeader("Authorization", "Bearer secret-token");
		request.addHeader("Cookie", "SESSION=secret-session");
		request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "unsafe request id\nforged");
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.filter.doFilter(request, response, (req, res) -> {
		});

		String generatedRequestId = response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
		assertThat(generatedRequestId).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

		List<ILoggingEvent> events = this.appender.list;
		assertThat(events).hasSize(1);
		String loggedData = events.getFirst().getFormattedMessage() + events.getFirst().getKeyValuePairs()
				+ events.getFirst().getMDCPropertyMap();

		assertThat(loggedData).contains("http.request.completed", "/api/v1/visits/{visitId}")
				.doesNotContain("secret-visit-id", "secret-token", "secret-session", "unsafe request id");
	}

	private static MockHttpServletRequest request(String method, String path) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/visits/{visitId}");
		return request;
	}
}
