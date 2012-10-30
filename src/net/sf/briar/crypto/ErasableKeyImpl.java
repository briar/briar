package net.sf.briar.crypto;

import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.util.ByteUtils;

class ErasableKeyImpl implements ErasableKey {

	private static final long serialVersionUID = -4438380720846443120L;

	private final byte[] key;
	private final String algorithm;

	private boolean erased = false; // Locking: this

	ErasableKeyImpl(byte[] key, String algorithm) {
		this.key = key;
		this.algorithm = algorithm;
	}

	public String getAlgorithm() {
		return algorithm;
	}

	public synchronized byte[] getEncoded() {
		if(erased) throw new IllegalStateException();
		return key;
	}

	public String getFormat() {
		return "RAW";
	}

	public ErasableKey copy() {
		return new ErasableKeyImpl(key.clone(), algorithm);
	}

	public synchronized void erase() {
		if(erased) throw new IllegalStateException();
		ByteUtils.erase(key);
		erased = true;
	}
}
