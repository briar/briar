package net.sf.briar.api.crypto;

/** The private half of a public/private {@link KeyPair}. */
public interface PrivateKey {

	/** Returns the encoded representation of this key. */
	byte[] getEncoded();
}
