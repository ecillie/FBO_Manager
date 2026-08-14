package com.ecillie.fbomanager.platform.api;

/**
 * Maps vendor/framework failures into the safe application persistence
 * vocabulary.
 */
public interface PersistenceExceptionMapper {

	PersistenceFailure map(RuntimeException failure);
}
