package com.ecillie.fbomanager.platform.api;

import java.util.List;
import java.util.Map;

public record ApiPageRequest(int page, int size, List<ApiSort> sort, Map<String, List<String>> filters) {

	public ApiPageRequest {
		if (page < 0 || size < 1 || size > 100) {
			throw new IllegalArgumentException("Page bounds are invalid");
		}
		sort = List.copyOf(sort);
		filters = filters.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
				entry -> List.copyOf(entry.getValue())));
	}
}
