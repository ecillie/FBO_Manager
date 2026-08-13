package com.ecillie.fbomanager.platform.internal.web;

import com.ecillie.fbomanager.platform.api.ApiCollectionResponse;
import com.ecillie.fbomanager.platform.api.ApiMeta;
import com.ecillie.fbomanager.platform.api.ApiPageMetadata;
import com.ecillie.fbomanager.platform.api.ApiPageRequest;
import com.ecillie.fbomanager.platform.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApiResponseFactory {

	private final Clock clock;

	ApiResponseFactory(Clock clock) {
		this.clock = clock;
	}

	public <T> ApiResponse<T> response(T data, HttpServletRequest request) {
		return new ApiResponse<>(data, meta(request));
	}

	public <T> ApiCollectionResponse<T> collection(List<T> data, ApiPageRequest page, long totalElements,
			HttpServletRequest request) {
		return new ApiCollectionResponse<>(data, ApiPageMetadata.of(page, totalElements), meta(request));
	}

	private ApiMeta meta(HttpServletRequest request) {
		return new ApiMeta(RequestCorrelationFilter.requestId(request),
				Instant.now(this.clock).truncatedTo(ChronoUnit.MICROS));
	}
}
