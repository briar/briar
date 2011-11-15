package net.sf.briar.api.crypto;

import javax.crypto.SecretKey;

public interface ErasableKey extends SecretKey {

	/** Erases the key from memory. */
	void erase();
}
