package org.briarproject.briar.client;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.util.ByteUtils;
import org.briarproject.briar.api.client.QueueMessage;
import org.briarproject.briar.api.client.QueueMessageFactory;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.bramble.util.ByteUtils.INT_64_BYTES;
import static org.briarproject.briar.api.client.QueueMessage.MAX_QUEUE_MESSAGE_BODY_LENGTH;
import static org.briarproject.briar.api.client.QueueMessage.QUEUE_MESSAGE_HEADER_LENGTH;

@Immutable
@NotNullByDefault
class QueueMessageFactoryImpl implements QueueMessageFactory {

	private final MessageFactory messageFactory;

	@Inject
	QueueMessageFactoryImpl(MessageFactory messageFactory) {
		this.messageFactory = messageFactory;
	}

	@Override
	public QueueMessage createMessage(GroupId groupId, long timestamp,
			long queuePosition, byte[] body) {
		if (body.length > MAX_QUEUE_MESSAGE_BODY_LENGTH)
			throw new IllegalArgumentException();
		byte[] messageBody = new byte[INT_64_BYTES + body.length];
		ByteUtils.writeUint64(queuePosition, messageBody, 0);
		System.arraycopy(body, 0, messageBody, INT_64_BYTES, body.length);
		Message m = messageFactory.createMessage(groupId, timestamp,
				messageBody);
		return new QueueMessage(m.getId(), groupId, timestamp, queuePosition,
				m.getRaw());
	}

	@Override
	public QueueMessage createMessage(MessageId id, byte[] raw) {
		if (raw.length < QUEUE_MESSAGE_HEADER_LENGTH)
			throw new IllegalArgumentException();
		if (raw.length > MAX_MESSAGE_LENGTH)
			throw new IllegalArgumentException();
		byte[] groupId = new byte[UniqueId.LENGTH];
		System.arraycopy(raw, 0, groupId, 0, UniqueId.LENGTH);
		long timestamp = ByteUtils.readUint64(raw, UniqueId.LENGTH);
		long queuePosition = ByteUtils.readUint64(raw, MESSAGE_HEADER_LENGTH);
		return new QueueMessage(id, new GroupId(groupId), timestamp,
				queuePosition, raw);
	}
}
