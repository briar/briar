package org.briarproject.api.sync;

import org.briarproject.api.UniqueId;

import java.nio.charset.Charset;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a {@link Group}.
 */
public class GroupId extends UniqueId {

	/**
	 * Label for hashing groups to calculate their identifiers.
	 */
	public static final byte[] LABEL =
			"GROUP_ID".getBytes(Charset.forName("US-ASCII"));

	public GroupId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof GroupId && super.equals(o);
	}
}
