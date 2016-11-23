package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.nio.charset.Charset;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a
 * {@link Message}.
 */
@ThreadSafe
@NotNullByDefault
public class MessageId extends UniqueId {

	/**
	 * Label for hashing messages to calculate their identifiers.
	 */
	public static final byte[] LABEL =
			"MESSAGE_ID".getBytes(Charset.forName("US-ASCII"));

	public MessageId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof MessageId && super.equals(o);
	}
}
