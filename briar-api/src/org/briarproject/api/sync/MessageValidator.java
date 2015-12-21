package org.briarproject.api.sync;

import org.briarproject.api.db.Metadata;
import org.briarproject.api.lifecycle.Service;

public interface MessageValidator extends Service {

	/**
	 * Validates the given message and returns its metadata if the message
	 * is valid, or null if the message is invalid.
	 */
	Metadata validateMessage(Message m);
}
