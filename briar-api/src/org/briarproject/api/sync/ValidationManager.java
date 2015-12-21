package org.briarproject.api.sync;

import org.briarproject.api.lifecycle.Service;

/**
 * Responsible for managing message validators and passing them messages to
 * validate.
 */
public interface ValidationManager extends Service {

	/** Sets the message validator for the given client. */
	void setMessageValidator(ClientId c, MessageValidator v);
}
