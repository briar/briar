package org.briarproject.briar.android.attachment;

import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.messaging.AttachmentHeader;

import javax.annotation.concurrent.Immutable;

@Immutable
class UnavailableItem {

	private final MessageId conversationMessageId;
	private final AttachmentHeader header;
	private final boolean needsSize;

	UnavailableItem(MessageId conversationMessageId,
			AttachmentHeader header, boolean needsSize) {
		this.conversationMessageId = conversationMessageId;
		this.header = header;
		this.needsSize = needsSize;
	}

	MessageId getConversationMessageId() {
		return conversationMessageId;
	}

	AttachmentHeader getHeader() {
		return header;
	}

	boolean needsSize() {
		return needsSize;
	}

}
