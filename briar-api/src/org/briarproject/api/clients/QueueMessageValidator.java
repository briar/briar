package org.briarproject.api.clients;

import org.briarproject.api.db.Metadata;
import org.briarproject.api.sync.Group;

public interface QueueMessageValidator {

	/**
	 * Validates the given message and returns its metadata if the message
	 * is valid, or null if the message is invalid.
	 */
	Metadata validateMessage(QueueMessage q, Group g);
}
