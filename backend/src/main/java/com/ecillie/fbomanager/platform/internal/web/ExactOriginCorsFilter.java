package com.ecillie.fbomanager.platform.internal.web;

import com.ecillie.fbomanager.platform.api.ApiException;
import com.ecillie.fbomanager.platform.internal.configuration.FboManagerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
final class ExactOriginCorsFilter extends OncePerRequestFilter {

	private static final Set<String> ALLOWED_METHODS = Set.of("DELETE", "GET", "OPTIONS", "PATCH", "POST", "PUT");
	private static final Set<String> ALLOWED_HEADERS = Set.of("accept", "content-type", "idempotency-key",
			"x-csrf-token", "x-request-id");
	private static final String EXPOSED_HEADERS = "X-Request-Id, Idempotency-Replayed, Location, Retry-After";

	private final String allowedOrigin;
	private final HandlerExceptionResolver exceptionResolver;

	ExactOriginCorsFilter(FboManagerProperties properties,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
		this.allowedOrigin = properties.web().allowedOrigin().toString();
		this.exceptionResolver = exceptionResolver;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String requestUri = request.getRequestURI();
		return !(requestUri.equals("/api/v1") || requestUri.startsWith("/api/v1/"));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String origin = request.getHeader(HttpHeaders.ORIGIN);
		if (origin == null) {
			filterChain.doFilter(request, response);
			return;
		}
		if (!this.allowedOrigin.equals(origin)) {
			deny(request, response);
			return;
		}

		response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, this.allowedOrigin);
		response.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
		response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
		response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, EXPOSED_HEADERS);

		String requestedMethod = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
		if ("OPTIONS".equals(request.getMethod()) && requestedMethod != null) {
			Set<String> requestedHeaders = requestedHeaders(
					request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS));
			if (!ALLOWED_METHODS.contains(requestedMethod) || !ALLOWED_HEADERS.containsAll(requestedHeaders)) {
				deny(request, response);
				return;
			}
			response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, String.join(", ", ALLOWED_METHODS));
			response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, String.join(", ", ALLOWED_HEADERS));
			response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "600");
			response.setStatus(HttpServletResponse.SC_OK);
			return;
		}

		filterChain.doFilter(request, response);
	}

	private void deny(HttpServletRequest request, HttpServletResponse response) {
		this.exceptionResolver.resolveException(request, response, null,
				new ApiException(HttpStatus.FORBIDDEN, "CORS_ORIGIN_DENIED", "The request origin is not allowed."));
	}

	private static Set<String> requestedHeaders(String header) {
		if (header == null || header.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(header.split(",")).map(String::trim).map(value -> value.toLowerCase(Locale.ROOT))
				.collect(Collectors.toUnmodifiableSet());
	}
}
