package org.briarproject.api.plugins;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;

import java.util.Collection;

/**
 * Keeps track of which contacts are currently connected by which transports.
 */
public interface ConnectionRegistry {

	void registerConnection(ContactId c, TransportId t);

	void unregisterConnection(ContactId c, TransportId t);

	Collection<ContactId> getConnectedContacts(TransportId t);

	boolean isConnected(ContactId c, TransportId t);

	boolean isConnected(ContactId c);
}
