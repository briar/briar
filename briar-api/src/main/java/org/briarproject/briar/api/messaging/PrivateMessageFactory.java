package org.briarproject.briar.api.messaging;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;

import java.util.List;

@NotNullByDefault
public interface PrivateMessageFactory {

	PrivateMessage createPrivateMessage(GroupId groupId, long timestamp,
			String text, List<AttachmentHeader> attachments)
			throws FormatException;

}
