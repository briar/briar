package net.sf.briar.crypto;

import java.util.Arrays;

import net.sf.briar.api.crypto.ErasableKey;

class ErasableKeyImpl implements ErasableKey {

	private static final long serialVersionUID = -4438380720846443120L;

	private final byte[] key;
	private final String algorithm;
	private boolean erased = false;

	ErasableKeyImpl(byte[] key, String algorithm) {
		this.key = key;
		this.algorithm = algorithm;
	}

	public String getAlgorithm() {
		return algorithm;
	}

	public byte[] getEncoded() {
		if(erased) throw new IllegalStateException();
		byte[] b = new byte[key.length];
		System.arraycopy(key, 0, b, 0, key.length);
		return b;
	}

	public String getFormat() {
		return "RAW";
	}

	public void erase() {
		if(erased) throw new IllegalStateException();
		for(int i = 0; i < key.length; i++) key[i] = 0;
		erased = true;
	}

	@Override
	public int hashCode() {
		// Not good, but the array can't be used because it's mutable
		return algorithm.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof ErasableKeyImpl) {
			ErasableKeyImpl e = (ErasableKeyImpl) o;
			return algorithm.equals(e.algorithm) && Arrays.equals(key, e.key);
		}
		return false;
	}
}
