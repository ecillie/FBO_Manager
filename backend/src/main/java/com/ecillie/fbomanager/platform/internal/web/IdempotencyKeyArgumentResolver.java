package com.ecillie.fbomanager.platform.internal.web;

import com.ecillie.fbomanager.platform.api.ApiConstants;
import com.ecillie.fbomanager.platform.api.ApiException;
import com.ecillie.fbomanager.platform.api.IdempotencyKey;
import com.ecillie.fbomanager.platform.api.IdempotentOperation;
import java.lang.reflect.Method;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

final class IdempotencyKeyArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType() == IdempotencyKey.class;
	}

	@Override
	public IdempotencyKey resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {

		Method method = parameter.getMethod();
		IdempotentOperation operation = method == null ? null : method.getAnnotation(IdempotentOperation.class);
		if (operation == null) {
			throw new IllegalStateException("IdempotencyKey parameters require @IdempotentOperation");
		}

		String value = webRequest.getHeader(ApiConstants.IDEMPOTENCY_KEY_HEADER);
		if (value == null || value.isBlank()) {
			if (operation.required()) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
						"A valid Idempotency-Key header is required for this operation.");
			}
			return null;
		}

		try {
			return new IdempotencyKey(value);
		} catch (IllegalArgumentException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
					"Idempotency-Key must contain 16 to 128 allowed ASCII characters.");
		}
	}
}
