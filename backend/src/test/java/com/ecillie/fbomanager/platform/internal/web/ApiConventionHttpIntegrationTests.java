package com.ecillie.fbomanager.platform.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecillie.fbomanager.platform.api.ApiException;
import com.ecillie.fbomanager.platform.internal.reference.ConventionReferenceApplicationService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"fbo.database.password=test-only-password", "fbo.database.url=jdbc:postgresql://127.0.0.1:1/unavailable",
		"fbo.database.readiness-timeout=1s", "fbo.web.maximum-body-size=1KB",
		"spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unavailable", "spring.datasource.username=fbo_app",
		"spring.datasource.password=test-only-password", "spring.datasource.hikari.connection-timeout=1000",
		"spring.datasource.hikari.initialization-fail-timeout=-1", "spring.jpa.hibernate.ddl-auto=none",
		"spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
		"spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false", "management.server.address=127.0.0.1",
		"management.server.port=0"})
@Import(ApiConventionHttpIntegrationTests.ErrorFixtureController.class)
class ApiConventionHttpIntegrationTests {

	private static final String VALID_BODY = """
			{
			  "referenceCode": "RAMP_SERVICE",
			  "sourceId": "9007199254740993",
			  "status": "ACTIVE",
			  "requestedQuantity": "125.000",
			  "quantityUnit": "GALLON",
			  "occurredAt": "2026-08-09T14:03:27.125Z"
			}
			""";

	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

	@LocalServerPort
	private int applicationPort;

	@org.springframework.beans.factory.annotation.Autowired
	private ConventionReferenceApplicationService service;

	@org.springframework.beans.factory.annotation.Autowired
	@Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;

	@Test
	void keepsEveryApplicationControllerRouteBelowTheVisibleMajorVersion() {
		this.mappings.getHandlerMethods().forEach((mapping, handler) -> {
			if (handler.getBeanType().getPackageName().startsWith("com.ecillie.fbomanager")) {
				assertThat(mapping.getPatternValues()).allMatch(path -> path.startsWith("/api/v1/"));
			}
		});
	}

	@Test
	void replacesAnInvalidInboundRequestIdAndUsesTheAuthoritativeValueInTheEnvelope() throws Exception {
		HttpResponse<String> response = send("GET", "/api/v1/platform-conventions", null,
				Map.of("X-Request-Id", "!not-allowed!"));

		String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
		assertThat(requestId).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
		assertThat(response.body()).contains("\"requestId\":\"" + requestId + "\"").doesNotContain("!not-allowed!");
	}

	@Test
	void returnsBoundedCollectionEnvelopeWithStableSortRequestIdAndSecurityHeaders() throws Exception {
		HttpResponse<String> response = send("GET",
				"/api/v1/platform-conventions?page=0&size=50&status=ACTIVE&sort=recordedAt,desc", null,
				Map.of("X-Request-Id", "client-request-123", "Accept", "application/json"));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
		assertThat(response.headers().firstValue("X-Request-Id")).hasValue("client-request-123");
		assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
		assertThat(response.headers().firstValue("Content-Security-Policy").orElseThrow())
				.contains("default-src 'self'", "frame-ancestors 'none'");
		assertThat(response.body()).contains("\"referenceId\":\"9007199254740993\"",
				"\"requestedQuantity\":\"125.000\"", "\"occurredAt\":\"2026-08-09T14:03:27.125Z\"", "\"number\":0",
				"\"size\":50", "\"totalElements\":1", "\"sort\":[\"recordedAt,desc\",\"referenceId,asc\"]",
				"\"requestId\":\"client-request-123\"");
		assertThat(response.body()).doesNotContain("reference_id", "numberValue");
	}

	@Test
	void appliesAllowedFiltersToBothContentAndCollectionTotals() throws Exception {
		HttpResponse<String> response = send("GET", "/api/v1/platform-conventions?status=INACTIVE", null, Map.of());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"data\":[]", "\"totalElements\":0", "\"totalPages\":0");
	}

	@Test
	void validatesRequestDtoAndIdempotencyHeaderBeforeTheApplicationServiceRuns() throws Exception {
		long invocationCount = this.service.invocationCount();
		String invalid = VALID_BODY.replace("\"ACTIVE\"", "\"active\"").replace("\"125.000\"", "\"0.00\"")
				.replace("2026-08-09T14:03:27.125Z", "2026-08-09T10:03:27-04:00")
				.replace("9007199254740993", "not-an-id");

		HttpResponse<String> response = send("POST", "/api/v1/platform-conventions", invalid,
				Map.of("Content-Type", "application/json", "Idempotency-Key", "018f47a2-31c0-7cc1-98aa-123456789abc"));

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.body()).contains("\"code\":\"REQUEST_VALIDATION_FAILED\"", "\"field\":\"sourceId\"",
				"\"code\":\"INVALID_IDENTIFIER\"", "\"field\":\"status\"", "\"code\":\"INVALID_ENUM\"",
				"\"field\":\"requestedQuantity\"", "\"code\":\"POSITIVE_DECIMAL_REQUIRED\"", "\"field\":\"occurredAt\"",
				"\"code\":\"INVALID_UTC_TIMESTAMP\"");
		assertThat(this.service.invocationCount()).isEqualTo(invocationCount);
	}

	@Test
	void returnsValidEchoUsingTransportTypesAfterValidation() throws Exception {
		long invocationCount = this.service.invocationCount();
		HttpResponse<String> response = send("POST", "/api/v1/platform-conventions", VALID_BODY, Map.of("Content-Type",
				"application/json; charset=UTF-8", "Idempotency-Key", "018f47a2-31c0-7cc1-98aa-123456789abc"));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"referenceId\":\"9007199254740993\"",
				"\"requestedQuantity\":\"125.000\"", "\"occurredAt\":\"2026-08-09T14:03:27.125Z\"");
		assertThat(this.service.invocationCount()).isEqualTo(invocationCount + 1);
	}

	@ParameterizedTest
	@MethodSource("invalidQueries")
	void rejectsUnboundedUnknownAndUnsupportedCollectionQueries(String query) throws Exception {
		HttpResponse<String> response = send("GET", "/api/v1/platform-conventions?" + query, null, Map.of());

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.body()).contains("\"code\":\"INVALID_QUERY\"");
	}

	static Stream<String> invalidQueries() {
		return Stream.of("size=0", "size=101", "page=-1", "sort=databaseColumn,asc", "sort=recordedAt,sideways",
				"unknown=value");
	}

	@Test
	void rejectsMissingOrMalformedIdempotencyKeys() throws Exception {
		long invocationCount = this.service.invocationCount();
		HttpResponse<String> missing = send("POST", "/api/v1/platform-conventions", VALID_BODY,
				Map.of("Content-Type", "application/json"));
		HttpResponse<String> malformed = send("POST", "/api/v1/platform-conventions", VALID_BODY,
				Map.of("Content-Type", "application/json", "Idempotency-Key", "short key"));

		assertThat(missing.statusCode()).isEqualTo(400);
		assertThat(missing.body()).contains("\"code\":\"IDEMPOTENCY_KEY_REQUIRED\"");
		assertThat(malformed.statusCode()).isEqualTo(400);
		assertThat(malformed.body()).contains("\"code\":\"INVALID_IDEMPOTENCY_KEY\"");
		assertThat(this.service.invocationCount()).isEqualTo(invocationCount);
	}

	@Test
	void rejectsUnknownJsonPropertiesOversizedBodiesRoutesVersionsAndMediaTypes() throws Exception {
		HttpResponse<String> unknownProperty = send("POST", "/api/v1/platform-conventions",
				VALID_BODY.replace("\n}", ",\n  \"secretField\": \"must-not-echo\"\n}"),
				Map.of("Content-Type", "application/json", "Idempotency-Key", "018f47a2-31c0-7cc1-98aa-123456789abc"));
		HttpResponse<String> tooLarge = send("POST", "/api/v1/platform-conventions", "x".repeat(1200),
				Map.of("Content-Type", "application/json", "Idempotency-Key", "018f47a2-31c0-7cc1-98aa-123456789abc"));
		HttpResponse<String> wrongVersion = send("GET", "/api/v2/platform-conventions", null, Map.of());
		HttpResponse<String> trailingSlash = send("GET", "/api/v1/platform-conventions/", null, Map.of());
		HttpResponse<String> wrongContentType = send("POST", "/api/v1/platform-conventions", VALID_BODY,
				Map.of("Content-Type", "text/plain", "Idempotency-Key", "018f47a2-31c0-7cc1-98aa-123456789abc"));

		assertThat(unknownProperty.statusCode()).isEqualTo(400);
		assertThat(unknownProperty.body()).contains("\"code\":\"INVALID_REQUEST\"").doesNotContain("must-not-echo");
		assertThat(tooLarge.statusCode()).isEqualTo(413);
		assertThat(tooLarge.body()).contains("\"code\":\"REQUEST_TOO_LARGE\"").doesNotContain("xxx");
		assertThat(tooLarge.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
		assertThat(wrongVersion.statusCode()).isEqualTo(404);
		assertThat(wrongVersion.body()).contains("\"code\":\"RESOURCE_NOT_FOUND\"");
		assertThat(trailingSlash.statusCode()).isEqualTo(404);
		assertThat(wrongContentType.statusCode()).isEqualTo(415);
		assertThat(wrongContentType.body()).contains("\"code\":\"UNSUPPORTED_MEDIA_TYPE\"");
	}

	@Test
	void permitsOnlyTheConfiguredExactCredentialedCorsOrigin() throws Exception {
		Map<String, String> preflight = Map.of("Origin", "http://localhost:5173", "Access-Control-Request-Method",
				"POST", "Access-Control-Request-Headers", "content-type,idempotency-key");
		HttpResponse<String> allowed = send("OPTIONS", "/api/v1/platform-conventions", null, preflight);
		HttpResponse<String> denied = send("OPTIONS", "/api/v1/platform-conventions", null,
				Map.of("Origin", "https://evil.example", "Access-Control-Request-Method", "POST"));

		assertThat(allowed.statusCode()).isEqualTo(200);
		assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://localhost:5173");
		assertThat(allowed.headers().firstValue("Access-Control-Allow-Credentials")).hasValue("true");
		assertThat(denied.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
		assertThat(denied.statusCode()).isEqualTo(403);
		assertThat(denied.body()).contains("\"code\":\"CORS_ORIGIN_DENIED\"");
		assertThat(denied.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
	}

	@ParameterizedTest
	@MethodSource("errorFamilies")
	void returnsStableSafeEnvelopeForEveryDocumentedErrorFamily(int status, String code) throws Exception {
		HttpResponse<String> response = send("GET", "/api/v1/test-errors/" + status, null,
				Map.of("X-Request-Id", "error-family-" + status));

		assertThat(response.statusCode()).isEqualTo(status);
		assertThat(response.body())
				.contains("\"status\":" + status, "\"code\":\"" + code + "\"",
						"\"requestId\":\"error-family-" + status + "\"", "\"retryable\":")
				.doesNotContain("database-password", "SQLException", "stackTrace", "internalIdentifier");
		assertThat(response.headers().firstValue("X-Request-Id")).hasValue("error-family-" + status);
		if (status == 429) {
			assertThat(response.headers().firstValue("Retry-After")).hasValue("3");
		}
	}

	static Stream<Arguments> errorFamilies() {
		return Stream.of(Arguments.of(400, "TEST_BAD_REQUEST"), Arguments.of(401, "TEST_UNAUTHORIZED"),
				Arguments.of(403, "TEST_FORBIDDEN"), Arguments.of(404, "TEST_NOT_FOUND"),
				Arguments.of(409, "TEST_CONFLICT"), Arguments.of(422, "TEST_UNPROCESSABLE_ENTITY"),
				Arguments.of(429, "TEST_TOO_MANY_REQUESTS"), Arguments.of(500, "INTERNAL_ERROR"),
				Arguments.of(503, "TEST_SERVICE_UNAVAILABLE"));
	}

	private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers)
			throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + this.applicationPort + path)).timeout(Duration.ofSeconds(5));
		headers.forEach(request::header);
		request.method(method,
				body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
		return this.httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	@RestController
	@RequestMapping("/api/v1/test-errors")
	static class ErrorFixtureController {

		@GetMapping("/{status}")
		Object error(@PathVariable int status) {
			if (status == 500) {
				throw new IllegalStateException("database-password SQLException internalIdentifier");
			}
			HttpStatus httpStatus = HttpStatus.valueOf(status);
			String code = status == 422 ? "TEST_UNPROCESSABLE_ENTITY" : "TEST_" + httpStatus.name();
			Duration retryAfter = status == 429 ? Duration.ofSeconds(3) : null;
			throw new ApiException(httpStatus, code, "A safe test error occurred.", status == 429 || status == 503,
					Map.of(), retryAfter);
		}
	}
}
