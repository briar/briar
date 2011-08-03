package net.sf.briar.api.protocol;

import net.sf.briar.api.serial.Writable;

/** A pseudonymous author of messages. */
public interface Author extends Writable {

	/** The maximum length of an author's name, in UTF-8 bytes. */
	static final int MAX_NAME_LENGTH = 50;

	/** The maximum length of an author's public key, in bytes. */
	static final int MAX_PUBLIC_KEY_LENGTH = 100;

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
