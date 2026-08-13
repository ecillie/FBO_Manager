package com.ecillie.fbomanager.platform.internal.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecillie.fbomanager.platform.internal.configuration.FboManagerProperties;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.unit.DataSize;

class GracefulLifecycleHooksTests {

	@Test
	void refusesTrafficBeforeGracefulShutdownWorkBegins() {
		AtomicReference<ReadinessState> readinessState = new AtomicReference<>();
		AtomicInteger availabilityChanges = new AtomicInteger();

		try (GenericApplicationContext context = new GenericApplicationContext()) {
			context.addApplicationListener(event -> {
				if (event instanceof AvailabilityChangeEvent<?> availabilityChange
						&& availabilityChange.getState() instanceof ReadinessState state) {
					readinessState.set(state);
					availabilityChanges.incrementAndGet();
				}
			});
			context.refresh();

			GracefulLifecycleHooks hooks = new GracefulLifecycleHooks(properties());
			hooks.prepareForShutdown(new ContextClosedEvent(context));
			hooks.prepareForShutdown(new ContextClosedEvent(context));

			assertThat(readinessState).hasValue(ReadinessState.REFUSING_TRAFFIC);
			assertThat(availabilityChanges).hasValue(1);
		}
	}

	private static FboManagerProperties properties() {
		return new FboManagerProperties(FboManagerProperties.DeploymentEnvironment.LOCAL,
				new FboManagerProperties.Database("jdbc:postgresql://localhost:5432/fbo_manager", "fbo_app",
						"test-only-password", Duration.ofSeconds(3)),
				new FboManagerProperties.Airport(ZoneId.of("America/New_York")),
				new FboManagerProperties.DiagnosticLogging(FboManagerProperties.DiagnosticLevel.INFO),
				new FboManagerProperties.Web(URI.create("http://localhost:5173"), DataSize.ofKilobytes(256)),
				new FboManagerProperties.Bindings(new FboManagerProperties.Binding("127.0.0.1", 8080),
						new FboManagerProperties.Binding("127.0.0.1", 8081)),
				new FboManagerProperties.Shutdown(Duration.ofSeconds(30)));
	}
}
