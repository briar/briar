package org.briarproject.briar.api.socialbackup;

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
public class ShardMessageHeader extends ConversationMessageHeader {

	private final List<AttachmentHeader> attachmentHeaders;

	public ShardMessageHeader(MessageId id, GroupId groupId, long timestamp,
			boolean local, boolean read, boolean sent, boolean seen,
			List<AttachmentHeader> headers) {
		super(id, groupId, timestamp, local, read, sent, seen);
		this.attachmentHeaders = headers;
	}

	public List<AttachmentHeader> getAttachmentHeaders() {
		return attachmentHeaders;
	}

	@Override
	public <T> T accept(ConversationMessageVisitor<T> v) {
		return v.visitShardMessage(this);
	}

}
