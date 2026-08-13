package com.ecillie.fbomanager.platform.internal.web;

import com.ecillie.fbomanager.platform.api.ApiException;
import com.ecillie.fbomanager.platform.api.ApiPageRequest;
import com.ecillie.fbomanager.platform.api.ApiSort;
import com.ecillie.fbomanager.platform.api.BoundedPage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

final class ApiPageRequestArgumentResolver implements HandlerMethodArgumentResolver {

	private static final int DEFAULT_PAGE_SIZE = 25;
	private static final int MAX_PAGE_SIZE = 100;
	private static final int MAX_FILTER_VALUES = 20;
	private static final int MAX_FILTER_VALUE_LENGTH = 128;

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType() == ApiPageRequest.class
				&& parameter.hasParameterAnnotation(BoundedPage.class);
	}

	@Override
	public ApiPageRequest resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {

		BoundedPage rules = parameter.getParameterAnnotation(BoundedPage.class);
		Map<String, String[]> parameters = webRequest.getParameterMap();
		Set<String> filters = Set.of(rules.allowedFilters());
		Set<String> known = new LinkedHashSet<>(filters);
		known.addAll(Set.of("page", "size", "sort"));

		parameters.keySet().stream().filter(name -> !known.contains(name)).findFirst().ifPresent(name -> {
			throw invalid("Unknown query parameter: " + name);
		});

		int page = parseBoundedInteger(single(parameters, "page"), "page", 0, Integer.MAX_VALUE, 0);
		int size = parseBoundedInteger(single(parameters, "size"), "size", 1, MAX_PAGE_SIZE, DEFAULT_PAGE_SIZE);
		List<ApiSort> sort = parseSort(parameters.get("sort"), rules);
		Map<String, List<String>> filterValues = parseFilters(parameters, filters);

		return new ApiPageRequest(page, size, sort, filterValues);
	}

	private static String single(Map<String, String[]> parameters, String name) {
		String[] values = parameters.get(name);
		if (values == null) {
			return null;
		}
		if (values.length != 1) {
			throw invalid(name + " may be supplied only once.");
		}
		return values[0];
	}

	private static int parseBoundedInteger(String value, String name, int minimum, int maximum, int defaultValue) {
		if (value == null) {
			return defaultValue;
		}
		if (!value.matches("0|[1-9][0-9]{0,9}")) {
			throw invalid(name + " must be a base-10 integer.");
		}
		try {
			int parsed = Integer.parseInt(value);
			if (parsed < minimum || parsed > maximum) {
				throw invalid(name + " is outside the allowed range.");
			}
			return parsed;
		} catch (NumberFormatException exception) {
			throw invalid(name + " is outside the allowed range.");
		}
	}

	private static List<ApiSort> parseSort(String[] values, BoundedPage rules) {
		List<String> requested = values == null ? Arrays.asList(rules.defaultSort()) : Arrays.asList(values);
		Set<String> allowed = Set.of(rules.allowedSorts());
		List<ApiSort> sort = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();

		for (String value : requested) {
			String[] parts = value.split(",", -1);
			if (parts.length != 2 || !allowed.contains(parts[0]) || !seen.add(parts[0])) {
				throw invalid("sort contains an unsupported or duplicate field.");
			}
			ApiSort.Direction direction;
			try {
				direction = ApiSort.Direction.valueOf(parts[1].toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException exception) {
				throw invalid("sort direction must be asc or desc.");
			}
			sort.add(new ApiSort(parts[0], direction));
		}

		if (!seen.contains(rules.tieBreaker())) {
			sort.add(new ApiSort(rules.tieBreaker(), ApiSort.Direction.ASC));
		}
		return sort;
	}

	private static Map<String, List<String>> parseFilters(Map<String, String[]> parameters, Set<String> filters) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		for (String filter : filters) {
			String[] values = parameters.get(filter);
			if (values == null) {
				continue;
			}
			if (values.length > MAX_FILTER_VALUES || Arrays.stream(values)
					.anyMatch(value -> value == null || value.isBlank() || value.length() > MAX_FILTER_VALUE_LENGTH)) {
				throw invalid("Filter values are missing or exceed their bounds.");
			}
			result.put(filter, Arrays.asList(values));
		}
		return result;
	}

	private static ApiException invalid(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", message);
	}
}
