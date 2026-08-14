package com.ecillie.fbomanager.platform.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Single boundary for UTC storage and airport-local interpretation. */
public interface AirportTime {

	Instant now();

	ZoneId zoneId();

	default Instant toInstant(LocalDateTime airportLocalTime) {
		return airportLocalTime.atZone(zoneId()).toInstant();
	}

	default LocalDate operatingDate(Instant instant) {
		return instant.atZone(zoneId()).toLocalDate();
	}
}
