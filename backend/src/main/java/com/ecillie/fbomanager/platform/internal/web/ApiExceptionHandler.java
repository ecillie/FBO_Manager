package com.ecillie.fbomanager.platform.internal.web;

import com.ecillie.fbomanager.platform.api.ApiException;
import com.ecillie.fbomanager.platform.api.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

	private final Clock clock;
	private final DatabaseFailureClassifier databaseFailureClassifier;

	ApiExceptionHandler(Clock clock, DatabaseFailureClassifier databaseFailureClassifier) {
		this.clock = clock;
		this.databaseFailureClassifier = databaseFailureClassifier;
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiErrorResponse> apiException(ApiException exception, HttpServletRequest request) {
		HttpHeaders headers = new HttpHeaders();
		if (exception.retryAfter() != null) {
			headers.set(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, exception.retryAfter().toSeconds())));
		}
		return response(exception.status(), exception.code(), exception.getMessage(), exception.retryable(), null,
				emptyToNull(exception.details()), headers, request);
	}

	@ExceptionHandler(DomainException.class)
	ResponseEntity<ApiErrorResponse> domainException(DomainException exception, HttpServletRequest request) {
		HttpStatus status = switch (exception.category()) {
			case VALIDATION -> HttpStatus.UNPROCESSABLE_CONTENT;
			case NOT_FOUND -> HttpStatus.NOT_FOUND;
			case CONFLICT -> HttpStatus.CONFLICT;
			case AUTHORIZATION -> HttpStatus.FORBIDDEN;
			case UNEXPECTED -> HttpStatus.INTERNAL_SERVER_ERROR;
		};
		HttpHeaders headers = new HttpHeaders();
		if (exception.retryAfter() != null) {
			headers.set(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, exception.retryAfter().toSeconds())));
		}
		return response(status, exception.code(), exception.getMessage(), exception.retryable(), null,
				emptyToNull(exception.details()), headers, request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
		List<ApiFieldError> errors = exception.getBindingResult().getFieldErrors().stream().map(this::fieldError)
				.sorted(Comparator.comparing(ApiFieldError::field).thenComparing(ApiFieldError::code)).toList();
		return response(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_FAILED", "The request contains invalid fields.",
				false, errors, null, new HttpHeaders(), request);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ApiErrorResponse> constraintViolation(ConstraintViolationException exception,
			HttpServletRequest request) {
		List<ApiFieldError> errors = exception.getConstraintViolations().stream().map(this::fieldError)
				.sorted(Comparator.comparing(ApiFieldError::field).thenComparing(ApiFieldError::code)).toList();
		return response(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_FAILED", "The request contains invalid fields.",
				false, errors, null, new HttpHeaders(), request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiErrorResponse> unreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
		if (hasCause(exception, RequestBodySizeFilter.BodyLimitIOException.class)) {
			return tooLarge(request);
		}
		return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
				"The request body is malformed or has invalid types.", false, null, null, new HttpHeaders(), request);
	}

	@ExceptionHandler(RequestBodyTooLargeException.class)
	ResponseEntity<ApiErrorResponse> tooLarge(RequestBodyTooLargeException exception, HttpServletRequest request) {
		return tooLarge(request);
	}

	@ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class,
			MethodArgumentTypeMismatchException.class, ConversionFailedException.class})
	ResponseEntity<ApiErrorResponse> invalidInput(Exception exception, HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is missing or has invalid input.",
				false, null, null, new HttpHeaders(), request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiErrorResponse> notFound(NoResourceFoundException exception, HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "The requested resource was not found.", false,
				null, null, new HttpHeaders(), request);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException exception,
			HttpServletRequest request) {
		return response(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "The HTTP method is not supported.", false,
				null, null, new HttpHeaders(), request);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ApiErrorResponse> unsupportedMedia(HttpMediaTypeNotSupportedException exception,
			HttpServletRequest request) {
		return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
				"Content-Type must be application/json for this request.", false, null, null, new HttpHeaders(),
				request);
	}

	@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
	ResponseEntity<ApiErrorResponse> unacceptableMedia(HttpMediaTypeNotAcceptableException exception,
			HttpServletRequest request) {
		return response(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE",
				"The requested response media type is not supported.", false, null, null, new HttpHeaders(), request);
	}

	@ExceptionHandler(DataAccessException.class)
	ResponseEntity<ApiErrorResponse> databaseFailure(DataAccessException exception, HttpServletRequest request) {
		return this.databaseFailureClassifier.classify(exception)
				.map(classified -> response(classified.status(), classified.code(), classified.message(),
						classified.retryable(), null, null, new HttpHeaders(), request))
				.orElseGet(() -> unexpected(exception, request));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
		LOGGER.atError().addKeyValue("event", "http.request.unexpected_failure")
				.addKeyValue("exceptionType", exception.getClass().getSimpleName()).log("Unexpected request failure");
		return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", false,
				null, null, new HttpHeaders(), request);
	}

	private ResponseEntity<ApiErrorResponse> tooLarge(HttpServletRequest request) {
		return response(HttpStatus.CONTENT_TOO_LARGE, "REQUEST_TOO_LARGE", "The request body exceeds the allowed size.",
				false, null, null, new HttpHeaders(), request);
	}

	private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message, boolean retryable,
			List<ApiFieldError> fields, Map<String, String> details, HttpHeaders headers, HttpServletRequest request) {
		headers.setContentType(MediaType.APPLICATION_JSON);
		ApiError error = new ApiError(status.value(), code, message, RequestCorrelationFilter.requestId(request),
				Instant.now(this.clock).truncatedTo(ChronoUnit.MICROS), retryable, fields, details);
		return new ResponseEntity<>(new ApiErrorResponse(error), headers, status);
	}

	private ApiFieldError fieldError(FieldError error) {
		return new ApiFieldError(error.getField(), validationCode(error.getCode()), safeValidationMessage(error));
	}

	private ApiFieldError fieldError(ConstraintViolation<?> violation) {
		String path = violation.getPropertyPath().toString();
		String field = path.substring(path.lastIndexOf('.') + 1);
		String annotation = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
		return new ApiFieldError(field, validationCode(annotation), violation.getMessage());
	}

	private static String validationCode(String annotation) {
		return switch (annotation == null ? "" : annotation) {
			case "NotBlank", "NotEmpty", "NotNull" -> "REQUIRED";
			case "Size" -> "INVALID_LENGTH";
			case "Positive", "PositiveOrZero" -> "POSITIVE_REQUIRED";
			case "ClosedEnum" -> "INVALID_ENUM";
			case "DecimalQuantity" -> "POSITIVE_DECIMAL_REQUIRED";
			case "GeneratedIdentifier" -> "INVALID_IDENTIFIER";
			case "UtcTimestamp" -> "INVALID_UTC_TIMESTAMP";
			default -> "INVALID_FORMAT";
		};
	}

	private static String safeValidationMessage(FieldError error) {
		String message = error.getDefaultMessage();
		return message == null || message.isBlank() ? "The value is invalid." : message;
	}

	private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
		Throwable current = failure;
		while (current != null) {
			if (type.isInstance(current)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private static Map<String, String> emptyToNull(Map<String, String> details) {
		return details.isEmpty() ? null : details;
	}
}
