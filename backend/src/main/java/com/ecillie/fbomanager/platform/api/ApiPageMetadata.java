package com.ecillie.fbomanager.platform.api;

import java.util.List;

public record ApiPageMetadata(int number, int size, long totalElements, int totalPages, List<String> sort) {

	public ApiPageMetadata {
		sort = List.copyOf(sort);
	}

	public static ApiPageMetadata of(ApiPageRequest request, long totalElements) {
		long pages = totalElements == 0 ? 0 : ((totalElements - 1) / request.size()) + 1;
		int totalPages = pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
		return new ApiPageMetadata(request.page(), request.size(), totalElements, totalPages,
				request.sort().stream().map(ApiSort::toString).toList());
	}
}
