package org.briarproject.messaging;

import org.briarproject.api.crypto.Signature;
import org.briarproject.api.data.Consumer;

/** A consumer that passes its input through a signature. */
class SigningConsumer implements Consumer {

	private final Signature signature;

	public SigningConsumer(Signature signature) {
		this.signature = signature;
	}

	public void write(byte b) {
		signature.update(b);
	}

	public void write(byte[] b, int off, int len) {
		signature.update(b, off, len);
	}
}
