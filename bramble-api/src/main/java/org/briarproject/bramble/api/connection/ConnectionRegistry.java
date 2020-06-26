package org.briarproject.bramble.api.connection;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.event.ConnectionClosedEvent;
import org.briarproject.bramble.api.plugin.event.ConnectionOpenedEvent;
import org.briarproject.bramble.api.plugin.event.ContactConnectedEvent;
import org.briarproject.bramble.api.plugin.event.ContactDisconnectedEvent;
import org.briarproject.bramble.api.rendezvous.event.RendezvousConnectionClosedEvent;
import org.briarproject.bramble.api.rendezvous.event.RendezvousConnectionOpenedEvent;
import org.briarproject.bramble.api.sync.Priority;

import java.util.Collection;

/**
 * Keeps track of which contacts are currently connected by which transports.
 */
@NotNullByDefault
public interface ConnectionRegistry {

	/**
	 * Registers an incoming connection from the given contact over the given
	 * transport. The connection's {@link Priority priority} can be set later
	 * via {@link #setPriority(ContactId, TransportId, InterruptibleConnection,
	 * Priority)} if a priority record is received from the contact.
	 * <p>
	 * Broadcasts {@link ConnectionOpenedEvent}. Also broadcasts
	 * {@link ContactConnectedEvent} if this is the only connection with the
	 * contact.
	 */
	void registerIncomingConnection(ContactId c, TransportId t,
			InterruptibleConnection conn);

	/**
	 * Registers an outgoing connection to the given contact over the given
	 * transport.
	 * <p>
	 * Broadcasts {@link ConnectionOpenedEvent}. Also broadcasts
	 * {@link ContactConnectedEvent} if this is the only connection with the
	 * contact.
	 * <p>
	 * If the registry has any "better" connections with the given contact, the
	 * given connection will be interrupted. If the registry has any "worse"
	 * connections with the given contact, those connections will be
	 * interrupted.
	 * <p>
	 * Connection A is considered "better" than connection B if both
	 * connections have had their priorities set, and either A's transport is
	 * {@link PluginConfig#getTransportPreferences() preferred} to B's, or
	 * they use the same transport and A has higher {@link Priority priority}
	 * than B.
	 * <p>
	 * For backward compatibility, connections without priorities are not
	 * considered better or worse than other connections.
	 */
	void registerOutgoingConnection(ContactId c, TransportId t,
			InterruptibleConnection conn, Priority priority);

	/**
	 * Unregisters a connection with the given contact over the given transport.
	 * <p>
	 * Broadcasts {@link ConnectionClosedEvent}. Also broadcasts
	 * {@link ContactDisconnectedEvent} if this is the only connection with
	 * the contact.
	 */
	void unregisterConnection(ContactId c, TransportId t,
			InterruptibleConnection conn, boolean incoming, boolean exception);

	/**
	 * Sets the {@link Priority priority} of a connection that was previously
	 * registered via {@link #registerIncomingConnection(ContactId, TransportId,
	 * InterruptibleConnection)}.
	 * <p>
	 * If the registry has any "better" connections with the given contact, the
	 * given connection will be interrupted. If the registry has any "worse"
	 * connections with the given contact, those connections will be
	 * interrupted.
	 * <p>
	 * Connection A is considered "better" than connection B if both
	 * connections have had their priorities set, and either A's transport is
	 * {@link PluginConfig#getTransportPreferences() preferred} to B's, or
	 * they use the same transport and A has higher {@link Priority priority}
	 * than B.
	 * <p>
	 * For backward compatibility, connections without priorities are not
	 * considered better or worse than other connections.
	 */
	void setPriority(ContactId c, TransportId t, InterruptibleConnection conn,
			Priority priority);

	/**
	 * Returns any contacts that are connected via the given transport.
	 */
	Collection<ContactId> getConnectedContacts(TransportId t);

	/**
	 * Returns any contacts that are connected via the given transport or any
	 * {@link PluginConfig#getTransportPreferences() better} transport.
	 */
	Collection<ContactId> getConnectedOrBetterContacts(TransportId t);

	/**
	 * Returns true if the given contact is connected via the given transport.
	 */
	boolean isConnected(ContactId c, TransportId t);

	/**
	 * Returns true if the given contact is connected via any transport.
	 */
	boolean isConnected(ContactId c);

	/**
	 * Registers a connection with the given pending contact. Broadcasts
	 * {@link RendezvousConnectionOpenedEvent} if this is the only connection
	 * with the pending contact.
	 *
	 * @return True if this is the only connection with the pending contact,
	 * false if it is redundant and should be closed
	 */
	boolean registerConnection(PendingContactId p);

	/**
	 * Unregisters a connection with the given pending contact. Broadcasts
	 * {@link RendezvousConnectionClosedEvent}.
	 */
	void unregisterConnection(PendingContactId p, boolean success);
}
