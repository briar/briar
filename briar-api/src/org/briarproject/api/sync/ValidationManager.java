package org.briarproject.api.sync;

/**
 * Responsible for managing message validators and passing them messages to
 * validate.
 */
public interface ValidationManager {

	/** Sets the message validator for the given client. */
	void setMessageValidator(ClientId c, MessageValidator v);
}
