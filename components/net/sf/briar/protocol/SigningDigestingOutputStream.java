package net.sf.briar.protocol;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.SignatureException;

/**
 * An output stream that passes its output through a signature and a message
 * digest.
 */
class SigningDigestingOutputStream extends FilterOutputStream {

	private final Signature signature;
	private final MessageDigest messageDigest;
	private boolean signing = false, digesting = false;

	public SigningDigestingOutputStream(OutputStream out, Signature signature,
			MessageDigest messageDigest) {
		super(out);
		this.signature = signature;
		this.messageDigest = messageDigest;
	}

	void setSigning(boolean signing) {
		this.signing = signing;
	}

	void setDigesting(boolean digesting) {
		this.digesting = digesting;
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		out.write(b, off, len);
		if(signing) {
			try {
				signature.update(b, off, len);
			} catch(SignatureException e) {
				throw new IOException(e.getMessage());
			}
		}
		if(digesting) messageDigest.update(b, off, len);
	}

	@Override
	public void write(int b) throws IOException {
		out.write(b);
		if(signing) {
			try {
				signature.update((byte) b);
			} catch(SignatureException e) {
				throw new IOException(e.getMessage());
			}
		}
		if(digesting) messageDigest.update((byte) b);
	}
}
