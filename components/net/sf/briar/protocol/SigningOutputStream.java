package net.sf.briar.protocol;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.Signature;
import java.security.SignatureException;

/** An output stream that passes its output through a signature. */
class SigningOutputStream extends FilterOutputStream {

	private final Signature signature;
	private boolean signing = false;

	public SigningOutputStream(OutputStream out, Signature signature) {
		super(out);
		this.signature = signature;
	}

	void setSigning(boolean signing) {
		this.signing = signing;
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
				throw new IOException(e);
			}
		}
	}

	@Override
	public void write(int b) throws IOException {
		out.write(b);
		if(signing) {
			try {
				signature.update((byte) b);
			} catch(SignatureException e) {
				throw new IOException(e);
			}
		}
	}
}
