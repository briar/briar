package org.briarproject.briar.api.messaging;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.briar.api.attachment.AttachmentHeader;

import java.util.List;

import javax.annotation.concurrent.Immutable;

import static java.util.Collections.emptyList;
import static org.briarproject.bramble.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
import static org.briarproject.briar.api.messaging.PrivateMessageFormat.TEXT_IMAGES;
import static org.briarproject.briar.api.messaging.PrivateMessageFormat.TEXT_IMAGES_AUTO_DELETE;
import static org.briarproject.briar.api.messaging.PrivateMessageFormat.TEXT_ONLY;

@Immutable
@NotNullByDefault
public class PrivateMessage {

	private final Message message;
	private final boolean hasText;
	private final List<AttachmentHeader> attachmentHeaders;
	private final long autoDeleteTimer;
	private final PrivateMessageFormat format;

	/**
	 * Constructor for private messages in the
	 * {@link PrivateMessageFormat#TEXT_ONLY TEXT_ONLY} format.
	 */
	public PrivateMessage(Message message) {
		this.message = message;
		hasText = true;
		attachmentHeaders = emptyList();
		autoDeleteTimer = NO_AUTO_DELETE_TIMER;
		format = TEXT_ONLY;
	}

	/**
	 * Constructor for private messages in the
	 * {@link PrivateMessageFormat#TEXT_IMAGES TEXT_IMAGES} format.
	 */
	public PrivateMessage(Message message, boolean hasText,
			List<AttachmentHeader> headers) {
		this.message = message;
		this.hasText = hasText;
		this.attachmentHeaders = headers;
		autoDeleteTimer = NO_AUTO_DELETE_TIMER;
		format = TEXT_IMAGES;
	}

	/**
	 * Constructor for private messages in the
	 * {@link PrivateMessageFormat#TEXT_IMAGES_AUTO_DELETE TEXT_IMAGES_AUTO_DELETE}
	 * format.
	 */
	public PrivateMessage(Message message, boolean hasText,
			List<AttachmentHeader> headers, long autoDeleteTimer) {
		this.message = message;
		this.hasText = hasText;
		this.attachmentHeaders = headers;
		this.autoDeleteTimer = autoDeleteTimer;
		format = TEXT_IMAGES_AUTO_DELETE;
	}

	public Message getMessage() {
		return message;
	}

	public PrivateMessageFormat getFormat() {
		return format;
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
}
