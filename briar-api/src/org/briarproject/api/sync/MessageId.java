package org.briarproject.api.sync;

import org.briarproject.api.UniqueId;

import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a
 * {@link Message}.
 */
public class MessageId extends UniqueId {

	/** Label for hashing messages to calculate their identifiers. */
	public static final byte[] LABEL =
			"MESSAGE_ID".getBytes(Charset.forName("US-ASCII"));

	public MessageId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof MessageId && Arrays.equals(id, ((MessageId) o).id);
	}
}
