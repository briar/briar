package org.briarproject.briar.android.conversation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class AttachmentItem {

	private final MessageId messageId;
	private final int width, height;
	private final int thumbnailWidth, thumbnailHeight;
	private final boolean hasError;

	AttachmentItem(MessageId messageId, int width, int height,
			int thumbnailWidth, int thumbnailHeight, boolean hasError) {
		this.messageId = messageId;
		this.width = width;
		this.height = height;
		this.thumbnailWidth = thumbnailWidth;
		this.thumbnailHeight = thumbnailHeight;
		this.hasError = hasError;
	}

	public MessageId getMessageId() {
		return messageId;
	}

	int getWidth() {
		return width;
	}

	int getHeight() {
		return height;
	}

	int getThumbnailWidth() {
		return thumbnailWidth;
	}

	int getThumbnailHeight() {
		return thumbnailHeight;
	}

	boolean hasError() {
		return hasError;
	}

}
