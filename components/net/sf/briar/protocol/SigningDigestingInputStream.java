package net.sf.briar.protocol;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.SignatureException;

/**
 * An input stream that passes its input through a signature and a message
 * digest. The signature and message digest lag behind the input by one byte
 * until the end of the input is reached, to allow users of this class to
 * maintain one byte of lookahead without affecting the signature or digest.
 */
class SigningDigestingInputStream extends FilterInputStream {

	private final Signature signature;
	private final MessageDigest messageDigest;
	private byte nextByte = 0;
	private boolean started = false, eof = false;
	private boolean signing = false, digesting = false;

	protected SigningDigestingInputStream(InputStream in, Signature signature,
			MessageDigest messageDigest) {
		super(in);
		this.signature = signature;
		this.messageDigest = messageDigest;
	}

	public void setSigning(boolean signing) {
		this.signing = signing;
	}

	public void setDigesting(boolean digesting) {
		this.digesting = digesting;
	}

	private void write(byte b) throws IOException {
		if(signing) {
			try {
				signature.update(b);
			} catch(SignatureException e) {
				throw new IOException(e);
			}
		}
		if(digesting) messageDigest.update(b);
	}

	private void write(byte[] b, int off, int len) throws IOException {
		if(signing) {
			try {
				signature.update(b, off, len);
			} catch(SignatureException e) {
				throw new IOException(e);
			}
		}
		if(digesting) messageDigest.update(b, off, len);
	}

	@Override
	public void mark(int readLimit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	@Override
	public int read() throws IOException {
		if(eof) return -1;
		if(started) write(nextByte);
		started = true;
		int i = in.read();
		if(i == -1) {
			eof = true;
			return -1;
		}
		nextByte = (byte) (i > 127 ? i - 256 : i);
		return i;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if(eof) return -1;
		if(started) write(nextByte);
		started = true;
		int read = in.read(b, off, len);
		if(read == -1) {
			eof = true;
			return -1;
		}
		if(read > 0) {
			write(b, off, read - 1);
			nextByte = b[off + read - 1];
		}
		return read;
	}

	@Override
	public void reset() {
		throw new UnsupportedOperationException();
	}

	@Override
	public long skip(long n) {
		throw new UnsupportedOperationException();
	}
}
