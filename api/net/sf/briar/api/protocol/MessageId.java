package net.sf.briar.api.protocol;

import java.util.Arrays;

/** Type-safe wrapper for a byte array that uniquely identifies a message. */
public class MessageId {

	/** Used to indicate that the first message in a thread has no parent. */
	public static final MessageId NONE = new MessageId(new byte[] {
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
	});

	public static final int LENGTH = 32;

	private final byte[] id;

	public MessageId(byte[] id) {
		assert id.length == LENGTH;
		this.id = id;
	}

	public byte[] getBytes() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof MessageId)
			return Arrays.equals(id, ((MessageId) o).id);
		return false;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(id);
	}
}
