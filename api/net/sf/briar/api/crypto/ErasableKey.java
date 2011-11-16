package net.sf.briar.api.crypto;

import javax.crypto.SecretKey;

public interface ErasableKey extends SecretKey {

	/** Returns a copy of the key. */
	ErasableKey copy();

	/** Erases the key from memory. */
	void erase();
}
