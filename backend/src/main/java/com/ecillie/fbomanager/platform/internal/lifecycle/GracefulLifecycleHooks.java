package com.ecillie.fbomanager.platform.internal.lifecycle;

import com.ecillie.fbomanager.platform.internal.configuration.FboManagerProperties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class GracefulLifecycleHooks {

	private static final Logger LOGGER = LoggerFactory.getLogger(GracefulLifecycleHooks.class);

	private final FboManagerProperties properties;
	private final AtomicBoolean readyLogged = new AtomicBoolean();
	private final AtomicBoolean shutdownStarted = new AtomicBoolean();

	public GracefulLifecycleHooks(FboManagerProperties properties) {
		this.properties = properties;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void applicationReady() {
		if (!this.readyLogged.compareAndSet(false, true)) {
			return;
		}

		LOGGER.atInfo().addKeyValue("event", "application.ready")
				.addKeyValue("airportTimezone", this.properties.airport().timezone().getId())
				.addKeyValue("apiPort", this.properties.bindings().api().port())
				.addKeyValue("managementPort", this.properties.bindings().management().port())
				.log("Application is ready to accept traffic");
	}

	@Order(Ordered.HIGHEST_PRECEDENCE)
	@EventListener(ContextClosedEvent.class)
	public void prepareForShutdown(ContextClosedEvent event) {
		if (!this.shutdownStarted.compareAndSet(false, true)) {
			return;
		}

		AvailabilityChangeEvent.publish(event.getApplicationContext(), ReadinessState.REFUSING_TRAFFIC);
		LOGGER.atInfo().addKeyValue("event", "application.shutdown.started")
				.addKeyValue("timeoutMs", this.properties.shutdown().timeout().toMillis())
				.log("Application is preparing for graceful shutdown");
	}
}
