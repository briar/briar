package org.briarproject.briar.api.messaging;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.attachment.AttachmentHeader;

import java.util.List;

import javax.annotation.Nullable;

@NotNullByDefault
public interface PrivateMessageFactory {

	PrivateMessage createLegacyPrivateMessage(GroupId groupId, long timestamp,
			String text) throws FormatException;

	PrivateMessage createPrivateMessage(GroupId groupId, long timestamp,
			@Nullable String text, List<AttachmentHeader> headers)
			throws FormatException;

	PrivateMessage createPrivateMessage(GroupId groupId, long timestamp,
			@Nullable String text, List<AttachmentHeader> headers,
			long autoDeleteTimer) throws FormatException;
}
