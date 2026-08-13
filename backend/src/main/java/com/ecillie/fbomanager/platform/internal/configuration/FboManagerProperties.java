package com.ecillie.fbomanager.platform.internal.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fbo")
public record FboManagerProperties(@NotNull DeploymentEnvironment environment, @Valid @NotNull Database database,
		@Valid @NotNull Airport airport, @Valid @NotNull DiagnosticLogging logging, @Valid @NotNull Web web,
		@Valid @NotNull Bindings bindings, @Valid @NotNull Shutdown shutdown) {

	private static final Duration MAXIMUM_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

	public enum DeploymentEnvironment {
		LOCAL, CI, DEVELOPMENT, NONPROD, PRODUCTION
	}

	public enum DiagnosticLevel {
		DEBUG, INFO, WARN, ERROR
	}

	public record Database(
			@NotBlank @Pattern(regexp = "^jdbc:postgresql://\\S+$", message = "must be a PostgreSQL JDBC URL") String url,
			@NotBlank String username, @NotBlank String password, @NotNull Duration readinessTimeout) {

		@AssertTrue(message = "URL must not contain embedded database credentials") public boolean isUrlCredentialFree() {
			if (this.url == null || !this.url.startsWith("jdbc:")) {
				return true;
			}

			try {
				return URI.create(this.url.substring("jdbc:".length())).getUserInfo() == null;
			} catch (IllegalArgumentException exception) {
				return false;
			}
		}

		@AssertTrue(message = "readiness-timeout must be greater than zero and no more than 5 seconds") public boolean isReadinessTimeoutInRange() {
			return this.readinessTimeout == null
					|| (!this.readinessTimeout.isZero() && !this.readinessTimeout.isNegative()
							&& this.readinessTimeout.compareTo(Duration.ofSeconds(5)) <= 0);
		}

		@Override
		public String toString() {
			return "Database[url=<redacted>, username=" + this.username + ", password=<redacted>, readinessTimeout="
					+ this.readinessTimeout + "]";
		}
	}

	public record Airport(@NotNull ZoneId timezone) {
	}

	public record DiagnosticLogging(@NotNull DiagnosticLevel level) {
	}

	public record Web(@NotNull URI allowedOrigin) {

		@AssertTrue(message = "allowed-origin must be an exact HTTP(S) origin without credentials, path, query, or fragment")
		public boolean isAllowedOriginExact() {
			if (this.allowedOrigin == null) {
				return true;
			}

			String scheme = this.allowedOrigin.getScheme();
			String path = this.allowedOrigin.getPath();
			boolean supportedScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);

			return supportedScheme && this.allowedOrigin.getHost() != null && this.allowedOrigin.getUserInfo() == null
					&& (path == null || path.isEmpty()) && this.allowedOrigin.getQuery() == null
					&& this.allowedOrigin.getFragment() == null;
		}
	}

	public record Bindings(@Valid @NotNull Binding api, @Valid @NotNull Binding management) {

		@AssertTrue(message = "API and management bindings must not use the same address and port") public boolean isManagementBindingDistinct() {
			if (this.api == null || this.management == null) {
				return true;
			}

			return this.api.port() != this.management.port()
					|| !this.api.address().equalsIgnoreCase(this.management.address());
		}
	}

	public record Binding(
			@NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9.:-]*$", message = "must be a hostname or IP address") String address,
			@Min(1) @Max(65_535) int port) {
	}

	public record Shutdown(@NotNull Duration timeout) {

		@AssertTrue(message = "timeout must be greater than zero and no more than 30 seconds") public boolean isTimeoutInRange() {
			return this.timeout == null || (!this.timeout.isZero() && !this.timeout.isNegative()
					&& this.timeout.compareTo(MAXIMUM_SHUTDOWN_TIMEOUT) <= 0);
		}
	}
}
