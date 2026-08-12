package com.ecillie.fbomanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "fbo.database.password=test-only-password")
class FboManagerApplicationTests {

	@Autowired
	private Clock clock;

	@Autowired
	@Qualifier("airportZoneId") private ZoneId airportZoneId;

	@Test
	void contextLoads() {
		assertThat(this.clock.getZone()).isEqualTo(ZoneId.of("Z"));
		assertThat(this.airportZoneId).isEqualTo(ZoneId.of("America/New_York"));
	}

}
