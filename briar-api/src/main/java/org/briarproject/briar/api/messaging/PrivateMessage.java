package org.briarproject.briar.api.messaging;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.briar.api.media.AttachmentHeader;

import java.util.List;

import javax.annotation.concurrent.Immutable;

import static java.util.Collections.emptyList;

@Immutable
@NotNullByDefault
public class PrivateMessage {

	private final Message message;
	private final boolean legacyFormat, hasText;
	private final List<AttachmentHeader> attachmentHeaders;

	/**
	 * Constructor for private messages in the legacy format, which does not
	 * support attachments.
	 */
	public PrivateMessage(Message message) {
		this.message = message;
		legacyFormat = true;
		hasText = true;
		attachmentHeaders = emptyList();
	}

	/**
	 * Constructor for private messages in the current format, which supports
	 * attachments.
	 */
	public PrivateMessage(Message message, boolean hasText,
			List<AttachmentHeader> headers) {
		this.message = message;
		this.hasText = hasText;
		this.attachmentHeaders = headers;
		legacyFormat = false;
	}

	public Message getMessage() {
		return message;
	}

	public boolean isLegacyFormat() {
		return legacyFormat;
	}

	public boolean hasText() {
		return hasText;
	}

	public List<AttachmentHeader> getAttachmentHeaders() {
		return attachmentHeaders;
	}
}
