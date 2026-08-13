package com.ecillie.fbomanager.platform.internal.web;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class ApiWebMvcConfiguration implements WebMvcConfigurer {

	@Override
	public void addArgumentResolvers(
			List<org.springframework.web.method.support.HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new ApiPageRequestArgumentResolver());
		resolvers.add(new IdempotencyKeyArgumentResolver());
	}
}
