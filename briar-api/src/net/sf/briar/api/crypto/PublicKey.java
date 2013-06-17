package net.sf.briar.api.crypto;

/** The public half of a public/private {@link KeyPair}. */
public interface PublicKey {

	/** Returns the encoded representation of this key. */
	byte[] getEncoded();
}
