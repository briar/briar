package org.briarproject.briar.api.client;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

@Deprecated
@NotNullByDefault
public interface QueueMessageFactory {

	QueueMessage createMessage(GroupId groupId, long timestamp,
			long queuePosition, byte[] body);

	QueueMessage createMessage(MessageId id, byte[] raw);
}
