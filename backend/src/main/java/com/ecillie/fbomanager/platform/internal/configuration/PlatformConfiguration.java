package com.ecillie.fbomanager.platform.internal.configuration;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FboManagerProperties.class)
class PlatformConfiguration {

	@Bean
	@ConditionalOnMissingBean(Clock.class)
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean("airportZoneId")
	ZoneId airportZoneId(FboManagerProperties properties) {
		return properties.airport().timezone();
	}
}
