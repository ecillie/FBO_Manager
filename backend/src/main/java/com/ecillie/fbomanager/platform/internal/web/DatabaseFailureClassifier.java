package com.ecillie.fbomanager.platform.internal.web;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
final class DatabaseFailureClassifier {

	private static final Map<String, String> UNIQUE_CONSTRAINT_CODES = Map.ofEntries(
			Map.entry("aircraft_visits_one_active_per_aircraft_uq", "AIRCRAFT_ALREADY_ACTIVE"),
			Map.entry("aircraft_visits_one_on_ramp_per_spot_uq", "PARKING_SPOT_OCCUPIED"),
			Map.entry("tasks_one_in_progress_per_worker_uq", "RESOURCE_UNAVAILABLE"),
			Map.entry("tasks_one_in_progress_per_vehicle_uq", "RESOURCE_UNAVAILABLE"),
			Map.entry("worker_shifts_one_in_progress_per_worker_uq", "WORKER_ALREADY_ON_SHIFT"),
			Map.entry("idempotency_records_pkey", "IDEMPOTENCY_IN_PROGRESS"));

	Optional<ClassifiedDatabaseFailure> classify(Throwable failure) {
		SQLException sqlException = findSqlException(failure);
		if (sqlException == null || sqlException.getSQLState() == null) {
			return Optional.empty();
		}

		String sqlState = sqlException.getSQLState();
		String constraint = constraintName(sqlException);
		return switch (sqlState) {
			case "23505" -> Optional.of(uniqueViolation(constraint));
			case "23503" -> Optional.of(new ClassifiedDatabaseFailure(HttpStatus.CONFLICT, "RELATED_RESOURCE_CONFLICT",
					"The requested relationship conflicts with current state.", false));
			case "23514" -> Optional.of(new ClassifiedDatabaseFailure(HttpStatus.UNPROCESSABLE_ENTITY,
					"DOMAIN_CONSTRAINT_VIOLATION", "The request violates an operational rule.", false));
			case "40001", "40P01", "55P03" ->
				Optional.of(new ClassifiedDatabaseFailure(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
						"The operation conflicted with another request. Retry explicitly.", true));
			case "57014" -> Optional.of(new ClassifiedDatabaseFailure(HttpStatus.SERVICE_UNAVAILABLE,
					"DATABASE_OPERATION_TIMEOUT", "The database operation did not complete in time.", true));
			case "57P01", "57P02", "57P03" -> Optional.of(unavailable());
			default -> sqlState.startsWith("08") ? Optional.of(unavailable()) : Optional.empty();
		};
	}

	private static ClassifiedDatabaseFailure uniqueViolation(String constraint) {
		String code = UNIQUE_CONSTRAINT_CODES.getOrDefault(constraint, "RESOURCE_ALREADY_EXISTS");
		String message = switch (code) {
			case "PARKING_SPOT_OCCUPIED" -> "The parking spot is no longer available.";
			case "RESOURCE_UNAVAILABLE" -> "The requested worker or vehicle is no longer available.";
			case "WORKER_ALREADY_ON_SHIFT" -> "The worker already has an active shift.";
			case "AIRCRAFT_ALREADY_ACTIVE" -> "The aircraft already has an active visit.";
			case "IDEMPOTENCY_IN_PROGRESS" -> "A matching operation is already in progress.";
			default -> "A resource with the requested unique values already exists.";
		};
		return new ClassifiedDatabaseFailure(HttpStatus.CONFLICT, code, message,
				"IDEMPOTENCY_IN_PROGRESS".equals(code));
	}

	private static ClassifiedDatabaseFailure unavailable() {
		return new ClassifiedDatabaseFailure(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE",
				"A required dependency is temporarily unavailable.", true);
	}

	private static SQLException findSqlException(Throwable failure) {
		Throwable current = failure;
		while (current != null) {
			if (current instanceof SQLException sqlException) {
				return sqlException;
			}
			current = current.getCause();
		}
		return null;
	}

	private static String constraintName(SQLException exception) {
		try {
			Object serverError = exception.getClass().getMethod("getServerErrorMessage").invoke(exception);
			if (serverError == null) {
				return null;
			}
			Object constraint = serverError.getClass().getMethod("getConstraint").invoke(serverError);
			return constraint instanceof String name ? name : null;
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
			return null;
		}
	}

	record ClassifiedDatabaseFailure(HttpStatus status, String code, String message, boolean retryable) {
	}
}
