package org.briarproject.bramble.api.plugin;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Collection;

/**
 * Keeps track of which contacts are currently connected by which transports.
 */
@NotNullByDefault
public interface ConnectionRegistry {

	void registerConnection(ContactId c, TransportId t, boolean incoming);

	void unregisterConnection(ContactId c, TransportId t, boolean incoming);

	Collection<ContactId> getConnectedContacts(TransportId t);

	boolean isConnected(ContactId c, TransportId t);

	boolean isConnected(ContactId c);
}
