package com.ecillie.fbomanager.platform.api;

import java.util.List;

public record RepositoryPage<T>(List<T> items, int offset, int limit, long total) {

	public RepositoryPage {
		items = List.copyOf(items);
		if (offset < 0 || limit < 1 || total < 0) {
			throw new IllegalArgumentException("invalid repository page metadata");
		}
	}
}
