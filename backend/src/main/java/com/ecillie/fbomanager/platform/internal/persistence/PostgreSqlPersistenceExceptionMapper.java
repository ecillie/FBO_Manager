package com.ecillie.fbomanager.platform.internal.persistence;

import com.ecillie.fbomanager.platform.api.PersistenceExceptionMapper;
import com.ecillie.fbomanager.platform.api.PersistenceFailure;
import com.ecillie.fbomanager.platform.api.PersistenceFailure.Kind;
import java.sql.SQLException;
import org.postgresql.util.PSQLException;
import org.springframework.stereotype.Component;

@Component
public class PostgreSqlPersistenceExceptionMapper implements PersistenceExceptionMapper {

	@Override
	public PersistenceFailure map(RuntimeException failure) {
		SQLException sqlFailure = findSqlFailure(failure);
		if (sqlFailure == null) {
			return new PersistenceFailure(Kind.UNEXPECTED, null, failure);
		}
		String sqlState = sqlFailure.getSQLState();
		String constraint = sqlFailure instanceof PSQLException postgres && postgres.getServerErrorMessage() != null
				? postgres.getServerErrorMessage().getConstraint()
				: null;
		return new PersistenceFailure(kind(sqlState), constraint, failure);
	}

	private static Kind kind(String sqlState) {
		if ("23505".equals(sqlState)) {
			return Kind.UNIQUE_CONFLICT;
		}
		if ("23503".equals(sqlState)) {
			return Kind.FOREIGN_KEY_CONFLICT;
		}
		if ("23514".equals(sqlState) || "23P01".equals(sqlState)) {
			return Kind.CHECK_VIOLATION;
		}
		if ("40P01".equals(sqlState) || "40001".equals(sqlState) || "55P03".equals(sqlState)) {
			return Kind.CONCURRENCY_CONFLICT;
		}
		if (sqlState != null && (sqlState.startsWith("08") || "57P01".equals(sqlState))) {
			return Kind.DEPENDENCY_UNAVAILABLE;
		}
		return Kind.UNEXPECTED;
	}

	private static SQLException findSqlFailure(Throwable failure) {
		Throwable current = failure;
		while (current != null) {
			if (current instanceof SQLException sqlException) {
				return sqlException;
			}
			current = current.getCause();
		}
		return null;
	}
}
