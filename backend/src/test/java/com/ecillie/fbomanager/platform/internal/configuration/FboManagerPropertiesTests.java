package com.ecillie.fbomanager.platform.internal.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FboManagerPropertiesTests {

	@Test
	void bindsValidatedImmutablePropertiesAndTimeBeans() {
		validContext().withPropertyValues("fbo.database.password=test-only-password").run(context -> {
			assertThat(context).hasNotFailed();

			FboManagerProperties properties = context.getBean(FboManagerProperties.class);
			assertThat(properties.environment()).isEqualTo(FboManagerProperties.DeploymentEnvironment.LOCAL);
			assertThat(properties.database().url()).isEqualTo("jdbc:postgresql://localhost:5432/fbo_manager");
			assertThat(properties.airport().timezone()).isEqualTo(ZoneId.of("America/New_York"));
			assertThat(properties.logging().level()).isEqualTo(FboManagerProperties.DiagnosticLevel.INFO);
			assertThat(properties.web().allowedOrigin()).hasToString("http://localhost:5173");
			assertThat(properties.bindings().api().port()).isEqualTo(8080);
			assertThat(properties.bindings().management().port()).isEqualTo(8081);
			assertThat(properties.shutdown().timeout()).isEqualTo(Duration.ofSeconds(30));
			assertThat(properties.database()).hasToString(
					"Database[url=<redacted>, username=fbo_app, password=<redacted>, readinessTimeout=PT3S]");
			assertThat(properties.toString()).doesNotContain("jdbc:postgresql://localhost:5432/fbo_manager",
					"test-only-password");

			assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Z"));
			assertThat(context.getBean("airportZoneId", ZoneId.class)).isEqualTo(ZoneId.of("America/New_York"));
		});
	}

	@Test
	void rejectsMissingDatabaseSecret() {
		validContext().run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasStackTraceContaining("database.password");
		});
	}

	@Test
	void rejectsUnsafeOriginDuplicateBindingAndExcessiveShutdownTimeout() {
		validContext().withPropertyValues("fbo.database.password=test-only-password",
				"fbo.web.allowed-origin=https://example.com/private", "fbo.bindings.management.port=8080",
				"fbo.shutdown.timeout=31s").run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).hasStackTraceContaining("allowed-origin")
							.hasStackTraceContaining("bindings").hasStackTraceContaining("timeout");
				});
	}

	private static ApplicationContextRunner validContext() {
		return new ApplicationContextRunner().withUserConfiguration(PlatformConfiguration.class).withPropertyValues(
				"fbo.environment=local", "fbo.database.url=jdbc:postgresql://localhost:5432/fbo_manager",
				"fbo.database.username=fbo_app", "fbo.database.readiness-timeout=3s",
				"fbo.airport.timezone=America/New_York", "fbo.logging.level=info",
				"fbo.web.allowed-origin=http://localhost:5173", "fbo.bindings.api.address=127.0.0.1",
				"fbo.bindings.api.port=8080", "fbo.bindings.management.address=127.0.0.1",
				"fbo.bindings.management.port=8081", "fbo.shutdown.timeout=30s");
	}
}
