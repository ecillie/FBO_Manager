package com.ecillie.fbomanager.platform.internal.web;

final class RequestBodyTooLargeException extends RuntimeException {

	RequestBodyTooLargeException() {
		super("Request body exceeds the configured limit");
	}
}
