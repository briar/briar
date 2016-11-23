package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.MessageDigest;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.spongycastle.crypto.Digest;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class DigestWrapper implements MessageDigest {

	private final Digest digest;

	DigestWrapper(Digest digest) {
		this.digest = digest;
	}

	@Override
	public byte[] digest() {
		byte[] hash = new byte[digest.getDigestSize()];
		digest.doFinal(hash, 0);
		return hash;
	}

	@Override
	public byte[] digest(byte[] input) {
		update(input);
		return digest();
	}

	@Override
	public int digest(byte[] buf, int offset, int len) {
		byte[] hash = digest();
		len = Math.min(len, hash.length);
		System.arraycopy(hash, 0, buf, offset, len);
		return len;
	}

	@Override
	public int getDigestLength() {
		return digest.getDigestSize();
	}

	@Override
	public void reset() {
		digest.reset();
	}

	@Override
	public void update(byte input) {
		digest.update(input);
	}

	@Override
	public void update(byte[] input) {
		digest.update(input, 0, input.length);
	}

	@Override
	public void update(byte[] input, int offset, int len) {
		digest.update(input, offset, len);
	}
}
