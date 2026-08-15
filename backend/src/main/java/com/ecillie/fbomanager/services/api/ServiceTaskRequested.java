package com.ecillie.fbomanager.services.api;

import java.time.Instant;

/** Synchronous transactional request to create the task linked to a service. */
public record ServiceTaskRequested(long serviceRequestId, long aircraftVisitId, String title, Instant dueAt) {
}
