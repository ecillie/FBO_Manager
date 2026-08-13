package com.ecillie.fbomanager.platform.internal.web;

import com.ecillie.fbomanager.platform.internal.configuration.FboManagerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
final class RequestBodySizeFilter extends OncePerRequestFilter {

	private final long maximumBodySize;
	private final HandlerExceptionResolver exceptionResolver;

	RequestBodySizeFilter(FboManagerProperties properties,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
		this.maximumBodySize = properties.web().maximumBodySize().toBytes();
		this.exceptionResolver = exceptionResolver;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		if (request.getContentLengthLong() > this.maximumBodySize) {
			this.exceptionResolver.resolveException(request, response, null, new RequestBodyTooLargeException());
			return;
		}

		try {
			filterChain.doFilter(new BoundedRequest(request, this.maximumBodySize), response);
		} catch (BodyLimitIOException exception) {
			this.exceptionResolver.resolveException(request, response, null, new RequestBodyTooLargeException());
		}
	}

	private static final class BoundedRequest extends HttpServletRequestWrapper {

		private final long limit;
		private ServletInputStream inputStream;

		private BoundedRequest(HttpServletRequest request, long limit) {
			super(request);
			this.limit = limit;
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			if (this.inputStream == null) {
				this.inputStream = new BoundedInputStream(super.getInputStream(), this.limit);
			}
			return this.inputStream;
		}

		@Override
		public BufferedReader getReader() throws IOException {
			return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
		}
	}

	private static final class BoundedInputStream extends ServletInputStream {

		private final ServletInputStream delegate;
		private final long limit;
		private long count;

		private BoundedInputStream(ServletInputStream delegate, long limit) {
			this.delegate = delegate;
			this.limit = limit;
		}

		@Override
		public int read() throws IOException {
			int value = this.delegate.read();
			if (value >= 0) {
				count(1);
			}
			return value;
		}

		@Override
		public int read(byte[] bytes, int offset, int length) throws IOException {
			int read = this.delegate.read(bytes, offset, length);
			if (read > 0) {
				count(read);
			}
			return read;
		}

		private void count(int bytes) throws BodyLimitIOException {
			this.count += bytes;
			if (this.count > this.limit) {
				throw new BodyLimitIOException();
			}
		}

		@Override
		public boolean isFinished() {
			return this.delegate.isFinished();
		}

		@Override
		public boolean isReady() {
			return this.delegate.isReady();
		}

		@Override
		public void setReadListener(ReadListener listener) {
			this.delegate.setReadListener(listener);
		}
	}

	static final class BodyLimitIOException extends IOException {
	}
}
