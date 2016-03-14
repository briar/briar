package org.briarproject.api.clients;

import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

public interface QueueMessageFactory {

	QueueMessage createMessage(GroupId groupId, long timestamp,
			long queuePosition, byte[] body);

	QueueMessage createMessage(MessageId id, byte[] raw);
}
