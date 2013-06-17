package net.sf.briar.crypto;

import net.sf.briar.api.crypto.SecretKey;
import net.sf.briar.util.ByteUtils;

class SecretKeyImpl implements SecretKey {

	private final byte[] key;

	private boolean erased = false; // Locking: this

	SecretKeyImpl(byte[] key) {
		this.key = key;
	}

	public synchronized byte[] getEncoded() {
		if(erased) throw new IllegalStateException();
		return key;
	}

	public SecretKey copy() {
		return new SecretKeyImpl(key.clone());
	}

	public synchronized void erase() {
		if(erased) throw new IllegalStateException();
		ByteUtils.erase(key);
		erased = true;
	}
}
