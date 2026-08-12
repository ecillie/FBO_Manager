package com.ecillie.fbomanager.platform.internal.health;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ecillie.fbomanager.platform.internal.configuration.FboManagerProperties;
import java.sql.SQLException;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class PostgresReadinessHealthIndicatorTests {

	private final Logger logger = (Logger) LoggerFactory.getLogger(PostgresReadinessHealthIndicator.class);
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@BeforeEach
	void attachAppender() {
		this.appender.start();
		this.logger.addAppender(this.appender);
	}

	@AfterEach
	void detachAppender() {
		this.logger.detachAppender(this.appender);
		this.appender.stop();
	}

	@Test
	void reportsUpWhenTheBoundedQuerySucceeds() {
		PostgresReadinessHealthIndicator indicator = new PostgresReadinessHealthIndicator(database(), ignored -> true);

		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void reportsDownWithoutLeakingConnectionFailures() {
		PostgresReadinessHealthIndicator indicator = new PostgresReadinessHealthIndicator(database(), ignored -> {
			throw new SQLException("secret-password at jdbc:postgresql://private-host/fbo_manager");
		});

		Health health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).isEmpty();
		assertThat(this.appender.list).hasSize(1);
		assertThat(this.appender.list.getFirst().getFormattedMessage()).isEqualTo("PostgreSQL readiness check failed")
				.doesNotContain("secret-password", "private-host");
	}

	private static FboManagerProperties.Database database() {
		return new FboManagerProperties.Database("jdbc:postgresql://localhost:5432/fbo_manager", "fbo_app",
				"test-only-password", Duration.ofSeconds(3));
	}
}
