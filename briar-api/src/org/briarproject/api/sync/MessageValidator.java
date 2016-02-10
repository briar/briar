package org.briarproject.api.sync;

import org.briarproject.api.db.Metadata;

public interface MessageValidator {

	/**
	 * Validates the given message and returns its metadata if the message
	 * is valid, or null if the message is invalid.
	 */
	Metadata validateMessage(Message m, Group g);
}
