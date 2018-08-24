package org.briarproject.bramble.test;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.util.ByteUtils;

import static org.briarproject.bramble.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

@NotNullByDefault
public class TestMessageFactory implements MessageFactory {

	@Override
	public Message createMessage(GroupId g, long timestamp, byte[] body) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Message createMessage(byte[] raw) {
		throw new UnsupportedOperationException();
	}

	@Override
	public byte[] getRawMessage(Message m) {
		byte[] body = m.getBody();
		byte[] raw = new byte[MESSAGE_HEADER_LENGTH + body.length];
		byte[] groupId = m.getGroupId().getBytes();
		System.arraycopy(groupId, 0, raw, 0, groupId.length);
		ByteUtils.writeUint64(m.getTimestamp(), raw, groupId.length);
		System.arraycopy(body, 0, raw, MESSAGE_HEADER_LENGTH, body.length);
		return raw;
	}
}
