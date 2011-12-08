package net.sf.briar.api.serial;

import java.io.IOException;
import java.security.Signature;
import java.security.SignatureException;

/** A consumer that passes its input through a signature. */
public class SigningConsumer implements Consumer {

	private final Signature signature;

	public SigningConsumer(Signature signature) {
		this.signature = signature;
	}

	public void write(byte b) throws IOException {
		try {
			signature.update(b);
		} catch(SignatureException e) {
			throw new IOException(e.toString());
		}
	}

	public void write(byte[] b, int off, int len) throws IOException {
		try {
			signature.update(b, off, len);
		} catch(SignatureException e) {
			throw new IOException(e.toString());
		}
	}
}
