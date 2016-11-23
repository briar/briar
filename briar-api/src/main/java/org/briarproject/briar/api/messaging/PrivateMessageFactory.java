package org.briarproject.briar.api.messaging;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;

@NotNullByDefault
public interface PrivateMessageFactory {

	PrivateMessage createPrivateMessage(GroupId groupId, long timestamp,
			String body) throws FormatException;

}
