package net.sf.briar.api.protocol;

import java.io.IOException;
import java.util.Arrays;

import net.sf.briar.api.serial.Writer;

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

	public void writeTo(Writer w) throws IOException {
		w.writeUserDefinedTag(Tags.MESSAGE_ID);
		w.writeBytes(id);
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof MessageId)
			return Arrays.equals(id, ((MessageId) o).id);
		return false;
	}
}
