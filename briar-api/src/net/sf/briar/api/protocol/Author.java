package net.sf.briar.api.protocol;

/** A pseudonymous author of messages. */
public interface Author {

	/** Returns the author's unique identifier. */
	AuthorId getId();

	/** Returns the author's name. */
	String getName();

	/**
	 * Returns the public key that is used to verify messages signed by the
	 * author.
	 */
	byte[] getPublicKey();
}
