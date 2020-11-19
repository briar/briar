package org.briarproject.briar.api.messaging;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.attachment.AttachmentHeader;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.conversation.ConversationMessageVisitor;

import java.util.List;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class PrivateMessageHeader extends ConversationMessageHeader {

	private final boolean hasText;
	private final List<AttachmentHeader> attachmentHeaders;
	private final long autoDeleteTimer;

	public PrivateMessageHeader(MessageId id, GroupId groupId, long timestamp,
			boolean local, boolean read, boolean sent, boolean seen,
			boolean hasText, List<AttachmentHeader> headers,
			long autoDeleteTimer) {
		super(id, groupId, timestamp, local, read, sent, seen);
		this.hasText = hasText;
		this.attachmentHeaders = headers;
		this.autoDeleteTimer = autoDeleteTimer;
	}

	public boolean hasText() {
		return hasText;
	}

	public List<AttachmentHeader> getAttachmentHeaders() {
		return attachmentHeaders;
	}

	public long getAutoDeleteTimer() {
		return autoDeleteTimer;
	}

	@Override
	public <T> T accept(ConversationMessageVisitor<T> v) {
		return v.visitPrivateMessageHeader(this);
	}
}
