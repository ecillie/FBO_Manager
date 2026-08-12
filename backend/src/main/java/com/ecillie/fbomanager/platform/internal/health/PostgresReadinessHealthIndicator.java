package com.ecillie.fbomanager.platform.internal.health;

import com.ecillie.fbomanager.platform.internal.configuration.FboManagerProperties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

@Component("postgres")
public class PostgresReadinessHealthIndicator implements HealthIndicator {

	private static final Logger LOGGER = LoggerFactory.getLogger(PostgresReadinessHealthIndicator.class);

	private final FboManagerProperties.Database database;
	private final SqlProbe probe;
	private final AtomicReference<Status> previousStatus = new AtomicReference<>(Status.UNKNOWN);

	@Autowired
	public PostgresReadinessHealthIndicator(FboManagerProperties properties) {
		this(properties.database(), PostgresReadinessHealthIndicator::queryDatabase);
	}

	PostgresReadinessHealthIndicator(FboManagerProperties.Database database, SqlProbe probe) {
		this.database = database;
		this.probe = probe;
	}

	@Override
	public Health health() {
		try {
			if (!this.probe.isReady(this.database)) {
				return unavailable();
			}

			logRecoveryOnce();
			return Health.up().build();
		} catch (SQLException exception) {
			return unavailable();
		}
	}

	private Health unavailable() {
		if (!Status.DOWN.equals(this.previousStatus.getAndSet(Status.DOWN))) {
			LOGGER.atWarn().addKeyValue("event", "dependency.health.failed").addKeyValue("dependency", "postgresql")
					.addKeyValue("operation", "readiness").addKeyValue("outcome", "failure")
					.log("PostgreSQL readiness check failed");
		}

		return Health.down().build();
	}

	private void logRecoveryOnce() {
		Status previous = this.previousStatus.getAndSet(Status.UP);

		if (Status.DOWN.equals(previous)) {
			LOGGER.atInfo().addKeyValue("event", "dependency.health.recovered").addKeyValue("dependency", "postgresql")
					.addKeyValue("operation", "readiness").addKeyValue("outcome", "success")
					.log("PostgreSQL readiness check recovered");
		}
	}

	private static boolean queryDatabase(FboManagerProperties.Database database) throws SQLException {
		Properties connectionProperties = new Properties();
		connectionProperties.setProperty("user", database.username());
		connectionProperties.setProperty("password", database.password());
		connectionProperties.setProperty("connectTimeout", timeoutSeconds(database.readinessTimeout()));
		connectionProperties.setProperty("socketTimeout", timeoutSeconds(database.readinessTimeout()));
		connectionProperties.setProperty("ApplicationName", "fbo-manager-readiness");

		try (Connection connection = DriverManager.getConnection(database.url(), connectionProperties);
				PreparedStatement statement = connection.prepareStatement("SELECT 1")) {
			statement.setQueryTimeout(Integer.parseInt(timeoutSeconds(database.readinessTimeout())));

			try (ResultSet result = statement.executeQuery()) {
				return result.next() && result.getInt(1) == 1;
			}
		}
	}

	private static String timeoutSeconds(Duration timeout) {
		long seconds = Math.max(1, timeout.toSeconds());
		return Long.toString(seconds);
	}

	@FunctionalInterface
	interface SqlProbe {

		boolean isReady(FboManagerProperties.Database database) throws SQLException;
	}
}
