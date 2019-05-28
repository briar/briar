package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.Multiset;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.event.ConnectionClosedEvent;
import org.briarproject.bramble.api.plugin.event.ConnectionOpenedEvent;
import org.briarproject.bramble.api.plugin.event.ContactConnectedEvent;
import org.briarproject.bramble.api.plugin.event.ContactDisconnectedEvent;
import org.briarproject.bramble.api.rendezvous.event.RendezvousConnectionClosedEvent;
import org.briarproject.bramble.api.rendezvous.event.RendezvousConnectionOpenedEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;

@ThreadSafe
@NotNullByDefault
class ConnectionRegistryImpl implements ConnectionRegistry {

	private static final Logger LOG =
			getLogger(ConnectionRegistryImpl.class.getName());

	private final EventBus eventBus;

	private final Object lock = new Object();
	@GuardedBy("lock")
	private final Map<TransportId, Multiset<ContactId>> contactConnections;
	@GuardedBy("lock")
	private final Multiset<ContactId> contactCounts;
	@GuardedBy("lock")
	private final Set<PendingContactId> connectedPendingContacts;

	@Inject
	ConnectionRegistryImpl(EventBus eventBus) {
		this.eventBus = eventBus;
		contactConnections = new HashMap<>();
		contactCounts = new Multiset<>();
		connectedPendingContacts = new HashSet<>();
	}

	@Override
	public void registerConnection(ContactId c, TransportId t,
			boolean incoming) {
		if (LOG.isLoggable(INFO)) {
			if (incoming) LOG.info("Incoming connection registered: " + t);
			else LOG.info("Outgoing connection registered: " + t);
		}
		boolean firstConnection = false;
		synchronized (lock) {
			Multiset<ContactId> m = contactConnections.get(t);
			if (m == null) {
				m = new Multiset<>();
				contactConnections.put(t, m);
			}
			m.add(c);
			if (contactCounts.add(c) == 1) firstConnection = true;
		}
		eventBus.broadcast(new ConnectionOpenedEvent(c, t, incoming));
		if (firstConnection) {
			LOG.info("Contact connected");
			eventBus.broadcast(new ContactConnectedEvent(c));
		}
	}

	@Override
	public void unregisterConnection(ContactId c, TransportId t,
			boolean incoming) {
		if (LOG.isLoggable(INFO)) {
			if (incoming) LOG.info("Incoming connection unregistered: " + t);
			else LOG.info("Outgoing connection unregistered: " + t);
		}
		boolean lastConnection = false;
		synchronized (lock) {
			Multiset<ContactId> m = contactConnections.get(t);
			if (m == null || !m.contains(c))
				throw new IllegalArgumentException();
			m.remove(c);
			if (contactCounts.remove(c) == 0) lastConnection = true;
		}
		eventBus.broadcast(new ConnectionClosedEvent(c, t, incoming));
		if (lastConnection) {
			LOG.info("Contact disconnected");
			eventBus.broadcast(new ContactDisconnectedEvent(c));
		}
	}

	@Override
	public Collection<ContactId> getConnectedContacts(TransportId t) {
		synchronized (lock) {
			Multiset<ContactId> m = contactConnections.get(t);
			if (m == null) return Collections.emptyList();
			List<ContactId> ids = new ArrayList<>(m.keySet());
			if (LOG.isLoggable(INFO))
				LOG.info(ids.size() + " contacts connected: " + t);
			return ids;
		}
	}

	@Override
	public boolean isConnected(ContactId c, TransportId t) {
		synchronized (lock) {
			Multiset<ContactId> m = contactConnections.get(t);
			return m != null && m.contains(c);
		}
	}

	@Override
	public boolean isConnected(ContactId c) {
		synchronized (lock) {
			return contactCounts.contains(c);
		}
	}

	@Override
	public boolean registerConnection(PendingContactId p) {
		boolean added;
		synchronized (lock) {
			added = connectedPendingContacts.add(p);
		}
		if (added) eventBus.broadcast(new RendezvousConnectionOpenedEvent(p));
		return added;
	}

	@Override
	public void unregisterConnection(PendingContactId p, boolean success) {
		synchronized (lock) {
			if (!connectedPendingContacts.remove(p))
				throw new IllegalArgumentException();
		}
		eventBus.broadcast(new RendezvousConnectionClosedEvent(p, success));
	}
}
