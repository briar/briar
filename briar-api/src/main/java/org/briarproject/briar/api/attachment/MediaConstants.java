package org.briarproject.briar.api.attachment;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public interface MediaConstants {

	// Metadata keys for messages
	String MSG_KEY_CONTENT_TYPE = "contentType";
	String MSG_KEY_DESCRIPTOR_LENGTH = "descriptorLength";

	/**
	 * The maximum length of an attachment's content type in UTF-8 bytes.
	 */
	int MAX_CONTENT_TYPE_BYTES = 80;

	/**
	 * The maximum allowed size of image attachments.
	 * TODO: Different limit for GIFs?
	 */
	int MAX_IMAGE_SIZE = MAX_MESSAGE_BODY_LENGTH - 100; // 6 * 1024 * 1024;
}
