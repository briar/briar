package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.Multiset;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.event.ConnectionClosedEvent;
import org.briarproject.bramble.api.plugin.event.ConnectionOpenedEvent;
import org.briarproject.bramble.api.plugin.event.ContactConnectedEvent;
import org.briarproject.bramble.api.plugin.event.ContactDisconnectedEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.logging.Level.INFO;

@ThreadSafe
@NotNullByDefault
class ConnectionRegistryImpl implements ConnectionRegistry {

	private static final Logger LOG =
			Logger.getLogger(ConnectionRegistryImpl.class.getName());

	private final EventBus eventBus;
	private final Lock lock = new ReentrantLock();

	// The following are locking: lock
	private final Map<TransportId, Multiset<ContactId>> connections;
	private final Multiset<ContactId> contactCounts;

	@Inject
	ConnectionRegistryImpl(EventBus eventBus) {
		this.eventBus = eventBus;
		connections = new HashMap<>();
		contactCounts = new Multiset<>();
	}

	@Override
	public void registerConnection(ContactId c, TransportId t,
			boolean incoming) {
		if (LOG.isLoggable(INFO)) {
			if (incoming) LOG.info("Incoming connection registered: " + t);
			else LOG.info("Outgoing connection registered: " + t);
		}
		boolean firstConnection = false;
		lock.lock();
		try {
			Multiset<ContactId> m = connections.get(t);
			if (m == null) {
				m = new Multiset<>();
				connections.put(t, m);
			}
			m.add(c);
			if (contactCounts.add(c) == 1) firstConnection = true;
		} finally {
			lock.unlock();
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
		lock.lock();
		try {
			Multiset<ContactId> m = connections.get(t);
			if (m == null) throw new IllegalArgumentException();
			m.remove(c);
			if (contactCounts.remove(c) == 0) lastConnection = true;
		} finally {
			lock.unlock();
		}
		eventBus.broadcast(new ConnectionClosedEvent(c, t, incoming));
		if (lastConnection) {
			LOG.info("Contact disconnected");
			eventBus.broadcast(new ContactDisconnectedEvent(c));
		}
	}

	@Override
	public Collection<ContactId> getConnectedContacts(TransportId t) {
		lock.lock();
		try {
			Multiset<ContactId> m = connections.get(t);
			if (m == null) return Collections.emptyList();
			List<ContactId> ids = new ArrayList<>(m.keySet());
			if (LOG.isLoggable(INFO))
				LOG.info(ids.size() + " contacts connected: " + t);
			return ids;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean isConnected(ContactId c, TransportId t) {
		lock.lock();
		try {
			Multiset<ContactId> m = connections.get(t);
			return m != null && m.contains(c);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean isConnected(ContactId c) {
		lock.lock();
		try {
			return contactCounts.contains(c);
		} finally {
			lock.unlock();
		}
	}
}
