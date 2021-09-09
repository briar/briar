package org.briarproject.briar.messaging;

import static java.util.concurrent.TimeUnit.DAYS;

interface MessagingConstants {

	// Metadata keys for messages
	String MSG_KEY_TIMESTAMP = "timestamp";
	String MSG_KEY_LOCAL = "local";
	String MSG_KEY_MSG_TYPE = "messageType";
	String MSG_KEY_HAS_TEXT = "hasText";
	String MSG_KEY_ATTACHMENT_HEADERS = "attachmentHeaders";
	String MSG_KEY_AUTO_DELETE_TIMER = "autoDeleteTimer";

	/**
	 * How long to keep incoming attachments that aren't listed by any private
	 * message before deleting them.
	 */
	long MISSING_ATTACHMENT_CLEANUP_DURATION_MS = DAYS.toMillis(28);
}
