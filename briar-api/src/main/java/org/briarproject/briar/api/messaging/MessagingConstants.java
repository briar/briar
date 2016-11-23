package org.briarproject.briar.api.messaging;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public interface MessagingConstants {

	/**
	 * The maximum length of a private message's body in bytes.
	 */
	int MAX_PRIVATE_MESSAGE_BODY_LENGTH = MAX_MESSAGE_BODY_LENGTH - 1024;
}
