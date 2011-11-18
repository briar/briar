package net.sf.briar.crypto;

import java.util.ArrayList;
import java.util.Collection;

import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.util.ByteUtils;

class ErasableKeyImpl implements ErasableKey {

	private static final long serialVersionUID = -4438380720846443120L;

	private final byte[] key;
	private final String algorithm;

	private Collection<byte[]> copies = null; // Locking: this
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
		byte[] b = new byte[key.length];
		System.arraycopy(key, 0, b, 0, key.length);
		if(copies == null) copies = new ArrayList<byte[]>();
		copies.add(b);
		return b;
	}

	public String getFormat() {
		return "RAW";
	}

	public ErasableKey copy() {
		return new ErasableKeyImpl(getEncoded(), algorithm);
	}

	public synchronized void erase() {
		if(erased) throw new IllegalStateException();
		ByteUtils.erase(key);
		if(copies != null) for(byte[] b : copies) ByteUtils.erase(b);
		erased = true;
	}
}
