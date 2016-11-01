package org.briarproject.api.messaging;

import org.briarproject.api.FormatException;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;

@NotNullByDefault
public interface PrivateMessageFactory {

	PrivateMessage createPrivateMessage(GroupId groupId, long timestamp,
			String body) throws FormatException;

}
