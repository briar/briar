package org.briarproject.bramble.api.crypto;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public class DecryptionException extends Exception {

	private final DecryptionResult result;

	public DecryptionException(DecryptionResult result) {
		this.result = result;
	}

	public DecryptionResult getDecryptionResult() {
		return result;
	}
}
