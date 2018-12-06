package org.briarproject.briar.api.messaging;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Message;

import java.util.List;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class PrivateMessage {

	private final Message message;
	private final List<AttachmentHeader> attachments;

	public PrivateMessage(Message message, List<AttachmentHeader> attachments) {
		this.message = message;
		this.attachments = attachments;
	}

	public Message getMessage() {
		return message;
	}

	public List<AttachmentHeader> getAttachmentHeaders() {
		return attachments;
	}
}
