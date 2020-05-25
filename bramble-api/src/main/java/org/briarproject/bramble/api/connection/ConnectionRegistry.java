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
	 * Registers a connection with the given contact over the given transport.
	 * <p>
	 * If the registry has any connections with the same contact and a
	 * {@link PluginConfig#getTransportPreferences() worse} transport, those
	 * connections will be
	 * {@link InterruptibleConnection#interruptOutgoingSession() interrupted}.
	 * <p>
	 * If the registry has any connections with the same contact and a better
	 * transport, the given connection will be interrupted.
	 * <p>
	 * Broadcasts {@link ConnectionOpenedEvent}. Also broadcasts
	 * {@link ContactConnectedEvent} if this is the only connection with the
	 * contact.
	 */
	void registerConnection(ContactId c, TransportId t,
			InterruptibleConnection conn, boolean incoming);

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
	 * registered via {@link #registerConnection(ContactId, TransportId,
	 * InterruptibleConnection, boolean)}.
	 * <p>
	 * If the registry has any connections with the same contact and transport
	 * and a lower {@link Priority priority}, those connections will be
	 * {@link InterruptibleConnection#interruptOutgoingSession() interrupted}.
	 * <p>
	 * If the registry has any connections with the same contact and transport
	 * and a higher priority, the given connection will be interrupted.
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
