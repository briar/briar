package org.briarproject.api.transport;

import java.util.Collection;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;

/**
 * Keeps track of which contacts are currently connected by which transports.
 */
public interface ConnectionRegistry {

	void addListener(ConnectionListener c);

	void removeListener(ConnectionListener c);

	void registerConnection(ContactId c, TransportId t);

	void unregisterConnection(ContactId c, TransportId t);

	Collection<ContactId> getConnectedContacts(TransportId t);

	boolean isConnected(ContactId c);
}
