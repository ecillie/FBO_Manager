package com.ecillie.fbomanager.platform.internal.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record ApiError(int status, String code, String message, String requestId, Instant timestamp, boolean retryable,
		List<ApiFieldError> fieldErrors, Map<String, String> details) {
}
