package net.sf.briar.api.protocol;

import java.util.Arrays;

/** Type-safe wrapper for a byte array that uniquely identifies a message. */
public class MessageId extends UniqueId {

	/** Used to indicate that the first message in a thread has no parent. */
	public static final MessageId NONE = new MessageId(new byte[] {
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
	});

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
