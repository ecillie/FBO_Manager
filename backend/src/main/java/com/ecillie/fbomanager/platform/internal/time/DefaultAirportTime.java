package com.ecillie.fbomanager.platform.internal.time;

import com.ecillie.fbomanager.platform.api.AirportTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class DefaultAirportTime implements AirportTime {

	private final Clock clock;
	private final ZoneId zoneId;

	public DefaultAirportTime(Clock clock, @Qualifier("airportZoneId") ZoneId zoneId) {
		this.clock = clock;
		this.zoneId = zoneId;
	}

	@Override
	public Instant now() {
		return Instant.now(this.clock);
	}

	@Override
	public ZoneId zoneId() {
		return this.zoneId;
	}
}
