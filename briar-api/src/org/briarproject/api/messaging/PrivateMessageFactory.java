package org.briarproject.api.messaging;

import org.briarproject.api.FormatException;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

public interface PrivateMessageFactory {

	PrivateMessage createPrivateMessage(GroupId groupId, long timestamp,
			MessageId parent, String contentType, byte[] body)
			throws FormatException;
}
