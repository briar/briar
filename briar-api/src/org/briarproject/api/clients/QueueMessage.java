package org.briarproject.api.clients;

import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;

import static org.briarproject.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

public class QueueMessage extends Message {

	public static final int QUEUE_MESSAGE_HEADER_LENGTH =
			MESSAGE_HEADER_LENGTH + 8;
	public static final int MAX_QUEUE_MESSAGE_BODY_LENGTH =
			MAX_MESSAGE_BODY_LENGTH - 8;

	private final long queuePosition;

	public QueueMessage(MessageId id, GroupId groupId, long timestamp,
			long queuePosition, byte[] raw) {
		super(id, groupId, timestamp, raw);
		this.queuePosition = queuePosition;
	}

	public long getQueuePosition() {
		return queuePosition;
	}
}
