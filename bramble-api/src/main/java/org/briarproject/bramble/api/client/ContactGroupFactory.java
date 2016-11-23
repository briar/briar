package org.briarproject.bramble.api.client;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;

@NotNullByDefault
public interface ContactGroupFactory {

	/**
	 * Creates a group that is not shared with any contacts.
	 */
	Group createLocalGroup(ClientId clientId);

	/**
	 * Creates a group for the given client to share with the given contact.
	 */
	Group createContactGroup(ClientId clientId, Contact contact);

	/**
	 * Creates a group for the given client to share between the given authors
	 * identified by their AuthorIds.
	 */
	Group createContactGroup(ClientId clientId, AuthorId authorId1,
			AuthorId authorId2);

}
