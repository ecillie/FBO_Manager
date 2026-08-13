package com.ecillie.fbomanager.platform.internal.web;

import com.ecillie.fbomanager.platform.api.ApiConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

	static final String REQUEST_ID_HEADER = ApiConstants.REQUEST_ID_HEADER;
	static final String REQUEST_ID_MDC_KEY = "requestId";
	static final String REQUEST_ID_ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".requestId";

	private static final Logger LOGGER = LoggerFactory.getLogger(RequestCorrelationFilter.class);
	private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
	private static final Set<String> SAFE_HTTP_METHODS = Set.of("DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST",
			"PUT", "TRACE");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String requestId = authoritativeRequestId(request.getHeader(REQUEST_ID_HEADER));
		String previousRequestId = MDC.get(REQUEST_ID_MDC_KEY);
		long startedAt = System.nanoTime();
		Throwable failure = null;

		MDC.put(REQUEST_ID_MDC_KEY, requestId);
		request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
		response.setHeader(REQUEST_ID_HEADER, requestId);

		try {
			filterChain.doFilter(request, response);
		} catch (IOException | ServletException | RuntimeException | Error exception) {
			failure = exception;
			throw exception;
		} finally {
			logCompletedRequest(request, response, startedAt, failure);
			restoreMdc(previousRequestId);
		}
	}

	static String requestId(HttpServletRequest request) {
		Object value = request.getAttribute(REQUEST_ID_ATTRIBUTE);
		return value instanceof String requestId ? requestId : UUID.randomUUID().toString();
	}

	private static String authoritativeRequestId(String candidate) {
		return candidate != null && VALID_REQUEST_ID.matcher(candidate).matches()
				? candidate
				: UUID.randomUUID().toString();
	}

	private static void logCompletedRequest(HttpServletRequest request, HttpServletResponse response, long startedAt,
			Throwable failure) {

		int status = failure == null ? response.getStatus() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		String route = routeTemplate(request);

		if (status < HttpServletResponse.SC_BAD_REQUEST && route.startsWith("/actuator")) {
			return;
		}

		long durationMs = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
		var logEvent = status < HttpServletResponse.SC_INTERNAL_SERVER_ERROR ? LOGGER.atInfo() : LOGGER.atWarn();

		logEvent.addKeyValue("event", "http.request.completed")
				.addKeyValue("httpMethod", safeHttpMethod(request.getMethod())).addKeyValue("route", route)
				.addKeyValue("status", status).addKeyValue("durationMs", durationMs)
				.addKeyValue("outcome", status < HttpServletResponse.SC_BAD_REQUEST ? "success" : "failure")
				.log("HTTP request completed");
	}

	private static String routeTemplate(HttpServletRequest request) {
		Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		return route instanceof String routeTemplate && routeTemplate.startsWith("/") ? routeTemplate : "unmatched";
	}

	private static String safeHttpMethod(String method) {
		return method != null && SAFE_HTTP_METHODS.contains(method) ? method : "OTHER";
	}

	private static void restoreMdc(String previousRequestId) {
		if (previousRequestId == null) {
			MDC.remove(REQUEST_ID_MDC_KEY);
		} else {
			MDC.put(REQUEST_ID_MDC_KEY, previousRequestId);
		}
	}
}
