package com.ecillie.fbomanager.platform.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiMeta(String requestId, Instant timestamp, Instant asOf) {

	public ApiMeta(String requestId, Instant timestamp) {
		this(requestId, timestamp, null);
	}
}
