package org.briarproject.briar.api.attachment;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class AttachmentHeader {

	private final MessageId messageId;
	private final String contentType;

	public AttachmentHeader(MessageId messageId, String contentType) {
		this.messageId = messageId;
		this.contentType = contentType;
	}

	public MessageId getMessageId() {
		return messageId;
	}

	public String getContentType() {
		return contentType;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof AttachmentHeader &&
				messageId.equals(((AttachmentHeader) o).messageId);
	}

	@Override
	public int hashCode() {
		return messageId.hashCode();
	}

}
