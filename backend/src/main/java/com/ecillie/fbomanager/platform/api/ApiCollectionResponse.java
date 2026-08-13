package com.ecillie.fbomanager.platform.api;

import java.util.List;

public record ApiCollectionResponse<T>(List<T> data, ApiPageMetadata page, ApiMeta meta) {

	public ApiCollectionResponse {
		data = List.copyOf(data);
	}
}
