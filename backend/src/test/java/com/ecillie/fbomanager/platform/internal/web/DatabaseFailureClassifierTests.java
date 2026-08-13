package com.ecillie.fbomanager.platform.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class DatabaseFailureClassifierTests {

	private final DatabaseFailureClassifier classifier = new DatabaseFailureClassifier();

	@Test
	void classifiesNamedUniqueConstraintsWithoutUsingDriverText() {
		Throwable failure = dataFailure("23505", "aircraft_visits_one_on_ramp_per_spot_uq");

		DatabaseFailureClassifier.ClassifiedDatabaseFailure classified = this.classifier.classify(failure)
				.orElseThrow();

		assertThat(classified.status()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(classified.code()).isEqualTo("PARKING_SPOT_OCCUPIED");
		assertThat(classified.message()).doesNotContain("secret", "SQL", "aircraft_visits_one_on_ramp_per_spot_uq");
	}

	@Test
	void classifiesForeignKeyCheckConcurrencyAndDependencySqlStates() {
		assertClassification("23503", HttpStatus.CONFLICT, "RELATED_RESOURCE_CONFLICT", false);
		assertClassification("23514", HttpStatus.UNPROCESSABLE_ENTITY, "DOMAIN_CONSTRAINT_VIOLATION", false);
		assertClassification("40P01", HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", true);
		assertClassification("40001", HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", true);
		assertClassification("55P03", HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", true);
		assertClassification("08006", HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", true);
	}

	@Test
	void leavesUnknownSqlStatesForTheGenericSafeHandler() {
		assertThat(this.classifier.classify(dataFailure("42601", null))).isEmpty();
	}

	private void assertClassification(String sqlState, HttpStatus status, String code, boolean retryable) {
		DatabaseFailureClassifier.ClassifiedDatabaseFailure classified = this.classifier
				.classify(dataFailure(sqlState, "internal_constraint")).orElseThrow();
		assertThat(classified.status()).isEqualTo(status);
		assertThat(classified.code()).isEqualTo(code);
		assertThat(classified.retryable()).isEqualTo(retryable);
		assertThat(classified.message()).doesNotContain("secret", "internal_constraint", sqlState);
	}

	private static Throwable dataFailure(String sqlState, String constraint) {
		return new DataIntegrityViolationException("SQL and driver secret",
				new ConstraintSQLException("driver secret", sqlState, constraint));
	}

	public static final class ConstraintSQLException extends SQLException {

		private final ConstraintError serverErrorMessage;

		ConstraintSQLException(String message, String sqlState, String constraint) {
			super(message, sqlState);
			this.serverErrorMessage = new ConstraintError(constraint);
		}

		public ConstraintError getServerErrorMessage() {
			return this.serverErrorMessage;
		}
	}

	public record ConstraintError(String constraint) {

		public String getConstraint() {
			return this.constraint;
		}
	}
}
