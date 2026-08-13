package com.ecillie.fbomanager.platform.internal.web;

import com.ecillie.fbomanager.platform.api.ApiCollectionResponse;
import com.ecillie.fbomanager.platform.api.ApiConstants;
import com.ecillie.fbomanager.platform.api.ApiPageRequest;
import com.ecillie.fbomanager.platform.api.ApiResponse;
import com.ecillie.fbomanager.platform.api.BoundedPage;
import com.ecillie.fbomanager.platform.api.FixedPrecisionQuantity;
import com.ecillie.fbomanager.platform.api.GeneratedId;
import com.ecillie.fbomanager.platform.api.IdempotencyKey;
import com.ecillie.fbomanager.platform.api.IdempotentOperation;
import com.ecillie.fbomanager.platform.internal.reference.ConventionReferenceApplicationService;
import com.ecillie.fbomanager.platform.internal.reference.ConventionReferenceCommand;
import com.ecillie.fbomanager.platform.internal.reference.ConventionReferenceResult;
import com.ecillie.fbomanager.platform.internal.reference.QuantityUnit;
import com.ecillie.fbomanager.platform.internal.reference.ReferenceStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = ApiConstants.V1_PATH + "/platform-conventions", produces = MediaType.APPLICATION_JSON_VALUE)
public class ConventionReferenceController {

	private final ConventionReferenceApplicationService service;
	private final ApiResponseFactory responses;

	public ConventionReferenceController(ConventionReferenceApplicationService service, ApiResponseFactory responses) {
		this.service = service;
		this.responses = responses;
	}

	@GetMapping
	public ApiCollectionResponse<ConventionReferenceResponse> list(
			@BoundedPage(allowedFilters = "status", allowedSorts = {"recordedAt", "referenceId"}, defaultSort = {
					"recordedAt,desc"}, tieBreaker = "referenceId") ApiPageRequest page,
			HttpServletRequest servletRequest) {

		ConventionReferenceResponse reference = new ConventionReferenceResponse(
				GeneratedId.from(9_007_199_254_740_993L), "TRANSPORT_REFERENCE", ReferenceStatus.ACTIVE,
				FixedPrecisionQuantity.from(new BigDecimal("125.000")), QuantityUnit.GALLON,
				Instant.parse("2026-08-09T14:03:27.125Z"));
		boolean matchesFilter = page.filters().getOrDefault("status", List.of("ACTIVE")).contains("ACTIVE");
		boolean included = matchesFilter && page.page() == 0;
		return this.responses.collection(included ? List.of(reference) : List.of(), page, matchesFilter ? 1 : 0,
				servletRequest);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@IdempotentOperation
	public ApiResponse<ConventionReferenceResponse> validate(@Valid @RequestBody ConventionReferenceRequest request,
			IdempotencyKey idempotencyKey, HttpServletRequest servletRequest) {

		ConventionReferenceCommand command = new ConventionReferenceCommand(request.referenceCode(),
				Long.parseLong(request.sourceId()), ReferenceStatus.valueOf(request.status()),
				new BigDecimal(request.requestedQuantity()), QuantityUnit.valueOf(request.quantityUnit()),
				Instant.parse(request.occurredAt()));
		ConventionReferenceResult result = this.service.handle(command);
		return this.responses.response(toResponse(result), servletRequest);
	}

	private static ConventionReferenceResponse toResponse(ConventionReferenceResult result) {
		return new ConventionReferenceResponse(GeneratedId.from(result.referenceId()), result.referenceCode(),
				result.status(), FixedPrecisionQuantity.from(result.requestedQuantity()), result.quantityUnit(),
				result.occurredAt());
	}
}
