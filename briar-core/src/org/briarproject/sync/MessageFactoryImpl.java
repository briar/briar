package org.briarproject.sync;

import com.google.inject.Inject;

import org.briarproject.api.UniqueId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.ByteUtils;

import java.io.IOException;

import static org.briarproject.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

class MessageFactoryImpl implements MessageFactory {

	private final CryptoComponent crypto;

	@Inject
	MessageFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	@Override
	public Message createMessage(GroupId groupId, long timestamp, byte[] body)
			throws IOException {
		if (body.length > MAX_MESSAGE_BODY_LENGTH)
			throw new IllegalArgumentException();
		byte[] raw = new byte[MESSAGE_HEADER_LENGTH + body.length];
		System.arraycopy(groupId.getBytes(), 0, raw, 0, UniqueId.LENGTH);
		ByteUtils.writeUint64(timestamp, raw, UniqueId.LENGTH);
		System.arraycopy(body, 0, raw, MESSAGE_HEADER_LENGTH, body.length);
		MessageId id = new MessageId(crypto.hash(MessageId.LABEL, raw));
		return new Message(id, groupId, timestamp, raw);
	}
}
