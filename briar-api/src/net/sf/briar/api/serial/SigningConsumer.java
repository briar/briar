package net.sf.briar.api.serial;

import net.sf.briar.api.crypto.Signature;

/** A consumer that passes its input through a signature. */
public class SigningConsumer implements Consumer {

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
