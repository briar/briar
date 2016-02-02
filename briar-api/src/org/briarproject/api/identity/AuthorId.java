package org.briarproject.api.identity;

import org.briarproject.api.UniqueId;

import java.nio.charset.Charset;

/**
 * Type-safe wrapper for a byte array that uniquely identifies an
 * {@link org.briarproject.api.identity.Author Author}.
 */
public class AuthorId extends UniqueId {

	/**
	 * Label for hashing authors to calculate their identities.
	 */
	public static final byte[] LABEL =
			"AUTHOR_ID".getBytes(Charset.forName("US-ASCII"));

	public AuthorId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof AuthorId && super.equals(o);
	}
}
