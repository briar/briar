package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.util.ByteUtils;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

@Immutable
@NotNullByDefault
class MessageFactoryImpl implements MessageFactory {

	private final CryptoComponent crypto;

	@Inject
	MessageFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	@Override
	public Message createMessage(GroupId g, long timestamp, byte[] body) {
		if (body.length > MAX_MESSAGE_BODY_LENGTH)
			throw new IllegalArgumentException();
		byte[] timeBytes = new byte[ByteUtils.INT_64_BYTES];
		ByteUtils.writeUint64(timestamp, timeBytes, 0);
		byte[] idHash =
				crypto.hash(MessageId.LABEL, g.getBytes(), timeBytes, body);
		MessageId id = new MessageId(idHash);
		byte[] raw = new byte[MESSAGE_HEADER_LENGTH + body.length];
		System.arraycopy(g.getBytes(), 0, raw, 0, UniqueId.LENGTH);
		ByteUtils.writeUint64(timestamp, raw, UniqueId.LENGTH);
		System.arraycopy(body, 0, raw, MESSAGE_HEADER_LENGTH, body.length);
		return new Message(id, g, timestamp, raw);
	}

	@Override
	public Message createMessage(MessageId m, byte[] raw) {
		if (raw.length < MESSAGE_HEADER_LENGTH)
			throw new IllegalArgumentException();
		byte[] groupId = new byte[UniqueId.LENGTH];
		System.arraycopy(raw, 0, groupId, 0, UniqueId.LENGTH);
		long timestamp = ByteUtils.readUint64(raw, UniqueId.LENGTH);
		return new Message(m, new GroupId(groupId), timestamp, raw);
	}
}
