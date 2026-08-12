package com.ecillie.fbomanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;

@SpringBootApplication
public class FboManagerApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(FboManagerApplication.class, args);

		if (context.getEnvironment().acceptsProfiles(Profiles.of("migrate"))) {
			context.close();
		}
	}

}
