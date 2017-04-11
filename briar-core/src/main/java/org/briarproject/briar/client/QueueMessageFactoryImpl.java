package org.briarproject.briar.client;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
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

	private final CryptoComponent crypto;

	@Inject
	QueueMessageFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	@Override
	public QueueMessage createMessage(GroupId groupId, long timestamp,
			long queuePosition, byte[] body) {
		if (body.length > MAX_QUEUE_MESSAGE_BODY_LENGTH)
			throw new IllegalArgumentException();
		byte[] raw = new byte[QUEUE_MESSAGE_HEADER_LENGTH + body.length];
		System.arraycopy(groupId.getBytes(), 0, raw, 0, UniqueId.LENGTH);
		ByteUtils.writeUint64(timestamp, raw, UniqueId.LENGTH);
		ByteUtils.writeUint64(queuePosition, raw, MESSAGE_HEADER_LENGTH);
		System.arraycopy(body, 0, raw, QUEUE_MESSAGE_HEADER_LENGTH,
				body.length);
		byte[] timeBytes = new byte[INT_64_BYTES];
		ByteUtils.writeUint64(timestamp, timeBytes, 0);
		byte[] bodyBytes = new byte[body.length + INT_64_BYTES];
		System.arraycopy(raw, MESSAGE_HEADER_LENGTH, bodyBytes, 0,
				body.length + INT_64_BYTES);
		MessageId id = new MessageId(
				crypto.hash(MessageId.LABEL, groupId.getBytes(), timeBytes,
						bodyBytes));
		return new QueueMessage(id, groupId, timestamp, queuePosition, raw);
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
