package org.briarproject.api.sync;

import org.briarproject.api.contact.Contact;

public interface PrivateGroupFactory {

	/** Creates a group for the given client to share with the given contact. */
	Group createPrivateGroup(ClientId clientId, Contact contact);
}
