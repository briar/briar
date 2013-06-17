package net.sf.briar.crypto;

import net.sf.briar.api.crypto.MessageDigest;

import org.spongycastle.crypto.Digest;

/**
 * A message digest that prevents length extension attacks - see Ferguson and
 * Schneier, <i>Practical Cryptography</i>, chapter 6.
 * <p>
 * "Let h be an interative hash function. The hash function h<sub>d</sub> is
 * defined by h<sub>d</sub> := h(h(m)), and has a claimed security level of
 * min(k, n/2) where k is the security level of h and n is the size of the hash
 * result."
 */
class DoubleDigest implements MessageDigest {

	private final Digest delegate;

	DoubleDigest(Digest delegate) {
		this.delegate = delegate;
	}

	public byte[] digest() {
		byte[] digest = new byte[delegate.getDigestSize()];
		delegate.doFinal(digest, 0); // h(m)
		delegate.update(digest, 0, digest.length);
		delegate.doFinal(digest, 0); // h(h(m))
		return digest;
	}

	public byte[] digest(byte[] input) {
		delegate.update(input, 0, input.length);
		return digest();
	}

	public int digest(byte[] buf, int offset, int len) {
		byte[] digest = digest();
		len = Math.min(len, digest.length);
		System.arraycopy(digest, 0, buf, offset, len);
		return len;
	}

	public int getDigestLength() {
		return delegate.getDigestSize();
	}

	public void reset() {
		delegate.reset();
	}

	public void update(byte input) {
		delegate.update(input);
	}

	public void update(byte[] input) {
		delegate.update(input, 0, input.length);
	}

	public void update(byte[] input, int offset, int len) {
		delegate.update(input, offset, len);
	}
}
