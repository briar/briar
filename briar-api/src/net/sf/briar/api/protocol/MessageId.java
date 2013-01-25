package net.sf.briar.api.protocol;

import java.util.Arrays;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a
 * {@link Message}.
 */
public class MessageId extends UniqueId {

	public MessageId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof MessageId)
			return Arrays.equals(id, ((MessageId) o).id);
		return false;
	}
}
