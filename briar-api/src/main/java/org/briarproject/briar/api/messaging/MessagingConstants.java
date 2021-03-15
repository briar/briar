package org.briarproject.briar.api.messaging;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public interface MessagingConstants {

	/**
	 * The maximum length of a private message's text in UTF-8 bytes.
	 */
	int MAX_PRIVATE_MESSAGE_TEXT_LENGTH = MAX_MESSAGE_BODY_LENGTH - 2048;

	/**
	 * The maximum length of an incoming private message's text in UTF-8 bytes.
	 * This is higher than MAX_PRIVATE_MESSAGE_TEXT_LENGTH for compatibility
	 * with older peers.
	 * <p>
	 * TODO: Remove after a reasonable migration period (added 2021-03-12).
	 */
	int MAX_PRIVATE_MESSAGE_INCOMING_TEXT_LENGTH =
			MAX_MESSAGE_BODY_LENGTH - 1024;

	/**
	 * The maximum number of attachments per private message.
	 */
	int MAX_ATTACHMENTS_PER_MESSAGE = 10;

}
