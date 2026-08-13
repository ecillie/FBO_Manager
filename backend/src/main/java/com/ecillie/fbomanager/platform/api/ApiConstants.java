package com.ecillie.fbomanager.platform.api;

public final class ApiConstants {

	public static final String V1_PATH = "/api/v1";
	public static final String JSON = "application/json";
	public static final String REQUEST_ID_HEADER = "X-Request-Id";
	public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
	public static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";

	private ApiConstants() {
	}
}
