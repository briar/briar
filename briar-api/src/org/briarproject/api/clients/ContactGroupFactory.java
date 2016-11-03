package org.briarproject.api.clients;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;

public interface ContactGroupFactory {

	/** Creates a group that is not shared with any contacts. */
	Group createLocalGroup(ClientId clientId);

	/** Creates a group for the given client to share with the given contact. */
	Group createContactGroup(ClientId clientId, Contact contact);

	/**
	 * Creates a group for the given client to share between the given authors
	 * identified by their AuthorIds.
	 */
	Group createContactGroup(ClientId clientId, AuthorId authorId1,
			AuthorId authorId2);

}
