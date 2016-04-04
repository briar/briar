package org.briarproject.api.clients;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;

public interface PrivateGroupFactory {

	/** Creates a group that is not shared with any contacts. */
	Group createLocalGroup(ClientId clientId);

	/** Creates a group for the given client to share with the given contact. */
	Group createPrivateGroup(ClientId clientId, Contact contact);
}
