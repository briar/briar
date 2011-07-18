package net.sf.briar.protocol;

import java.io.IOException;
import java.security.Signature;
import java.security.SignatureException;

import net.sf.briar.api.serial.Consumer;

/** A consumer that passes its input through a signature. */
class SigningConsumer implements Consumer {

	private final Signature signature;

	SigningConsumer(Signature signature) {
		this.signature = signature;
	}

	public void write(byte b) throws IOException {
		try {
			signature.update(b);
		} catch(SignatureException e) {
			throw new IOException(e.getMessage());
		}
	}

	public void write(byte[] b) throws IOException {
		try {
			signature.update(b);
		} catch(SignatureException e) {
			throw new IOException(e.getMessage());
		}
	}

	public void write(byte[] b, int off, int len) throws IOException {
		try {
			signature.update(b, off, len);
		} catch(SignatureException e) {
			throw new IOException(e.getMessage());
		}
	}
}
