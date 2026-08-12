package com.ecillie.fbomanager.platform.internal.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"fbo.database.password=test-only-password", "fbo.database.url=jdbc:postgresql://127.0.0.1:1/unavailable",
		"fbo.database.readiness-timeout=1s", "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unavailable",
		"spring.datasource.username=fbo_app", "spring.datasource.password=test-only-password",
		"spring.datasource.hikari.connection-timeout=1000", "spring.datasource.hikari.initialization-fail-timeout=-1",
		"spring.jpa.hibernate.ddl-auto=none", "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
		"spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false", "management.server.address=127.0.0.1",
		"management.server.port=0"})
class HealthEndpointIntegrationTests {

	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

	@LocalServerPort
	private int applicationPort;

	@LocalManagementPort
	private int managementPort;

	@Test
	void exposesProcessOnlyLivenessOnThePrivateManagementServer() throws Exception {
		HttpResponse<String> response = get(this.managementPort, "/actuator/health/liveness");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
	}

	@Test
	void reportsDatabaseFailureAsReadinessWithoutDetails() throws Exception {
		HttpResponse<String> response = get(this.managementPort, "/actuator/health/readiness");

		assertThat(response.statusCode()).isEqualTo(503);
		assertThat(response.body()).isEqualTo("{\"status\":\"DOWN\"}");
	}

	@Test
	void doesNotExposeActuatorOnTheApplicationPort() throws Exception {
		HttpResponse<String> response = get(this.applicationPort, "/actuator/health/liveness");

		assertThat(response.statusCode()).isEqualTo(404);
	}

	private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + port + path))
				.timeout(Duration.ofSeconds(5)).GET().build();

		return this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
