package org.briarproject.briar.api.messaging;

public enum PrivateMessageFormat {

	/**
	 * First version of the private message format, which doesn't support
	 * image attachments or auto-deletion.
	 */
	TEXT_ONLY,

	/**
	 * Second version of the private message format, which supports image
	 * attachments but not auto-deletion. Support for this format was
	 * added in client version 0.1.
	 */
	TEXT_IMAGES,

	/**
	 * Third version of the private message format, which supports image
	 * attachments and auto-deletion. Support for this format was added
	 * in client version 0.3.
	 */
	TEXT_IMAGES_AUTO_DELETE
}
