package com.ecillie.modulithfixture.beta;

import com.ecillie.modulithfixture.alpha.internal.AlphaSecret;

final class InvalidConsumer {

	private final AlphaSecret secret = new AlphaSecret();

	AlphaSecret secret() {
		return this.secret;
	}
}
