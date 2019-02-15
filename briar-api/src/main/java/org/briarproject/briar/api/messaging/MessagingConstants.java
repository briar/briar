package org.briarproject.briar.api.messaging;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public interface MessagingConstants {

	/**
	 * The maximum length of a private message's text in UTF-8 bytes.
	 */
	int MAX_PRIVATE_MESSAGE_TEXT_LENGTH = MAX_MESSAGE_BODY_LENGTH - 1024;

	/**
	 * The supported mime types for image attachments.
	 */
	String[] IMAGE_MIME_TYPES = {
			"image/jpeg",
			"image/png",
			"image/gif",
	};

	/**
	 * The maximum allowed size of image attachments.
	 */
	int MAX_IMAGE_SIZE = 6 * 1024 * 1024;

}
