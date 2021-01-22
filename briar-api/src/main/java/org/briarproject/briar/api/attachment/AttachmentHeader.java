package org.briarproject.briar.api.attachment;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class AttachmentHeader {

	private final GroupId groupId;
	private final MessageId messageId;
	private final String contentType;

	public AttachmentHeader(GroupId groupId, MessageId messageId,
			String contentType) {
		this.groupId = groupId;
		this.messageId = messageId;
		this.contentType = contentType;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public MessageId getMessageId() {
		return messageId;
	}

	public String getContentType() {
		return contentType;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof AttachmentHeader) {
			AttachmentHeader h = (AttachmentHeader) o;
			return groupId.equals(h.groupId) && messageId.equals(h.messageId);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return messageId.hashCode();
	}
}
