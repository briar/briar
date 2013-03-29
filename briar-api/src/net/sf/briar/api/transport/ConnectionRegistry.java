package net.sf.briar.api.transport;

import java.util.Collection;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;

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
