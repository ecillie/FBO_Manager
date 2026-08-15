package com.ecillie.fbomanager.platform.api;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Executes and records a retry-safe command in the caller's database
 * transaction.
 */
public interface IdempotentCommandExecutor {

	<T> IdempotentResult<T> execute(String operationId, IdempotencyKey key, Object command, ActorContext actor,
			int responseStatus, Duration retention, Class<T> resultType, Supplier<T> action);

	<T> IdempotentResult<T> executeInventory(String operationId, IdempotencyKey key, Object command, ActorContext actor,
			int responseStatus, Class<T> resultType, Function<T, Long> evidenceTransaction, Supplier<T> action);
}
