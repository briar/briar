package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.MessageDigest;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.spongycastle.crypto.Digest;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A message digest that prevents length extension attacks - see Ferguson and
 * Schneier, <i>Practical Cryptography</i>, chapter 6.
 * <p>
 * "Let h be an interative hash function. The hash function h<sub>d</sub> is
 * defined by h<sub>d</sub> := h(h(m)), and has a claimed security level of
 * min(k, n/2) where k is the security level of h and n is the size of the hash
 * result."
 */
@NotThreadSafe
@NotNullByDefault
class DoubleDigest implements MessageDigest {

	private final Digest delegate;

	DoubleDigest(Digest delegate) {
		this.delegate = delegate;
	}

	@Override
	public byte[] digest() {
		byte[] digest = new byte[delegate.getDigestSize()];
		delegate.doFinal(digest, 0); // h(m)
		delegate.update(digest, 0, digest.length);
		delegate.doFinal(digest, 0); // h(h(m))
		return digest;
	}

	@Override
	public byte[] digest(byte[] input) {
		delegate.update(input, 0, input.length);
		return digest();
	}

	@Override
	public int digest(byte[] buf, int offset, int len) {
		byte[] digest = digest();
		len = Math.min(len, digest.length);
		System.arraycopy(digest, 0, buf, offset, len);
		return len;
	}

	@Override
	public int getDigestLength() {
		return delegate.getDigestSize();
	}

	@Override
	public void reset() {
		delegate.reset();
	}

	@Override
	public void update(byte input) {
		delegate.update(input);
	}

	@Override
	public void update(byte[] input) {
		delegate.update(input, 0, input.length);
	}

	@Override
	public void update(byte[] input, int offset, int len) {
		delegate.update(input, offset, len);
	}
}
