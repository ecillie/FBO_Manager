package com.ecillie.fbomanager.platform.api;

/** Authoritative command outcome plus replay metadata for the HTTP boundary. */
public record IdempotentResult<T>(T value, int responseStatus, boolean replayed) {
}
