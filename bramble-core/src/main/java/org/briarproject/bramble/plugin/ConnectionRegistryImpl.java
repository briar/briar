package org.briarproject.bramble.plugin;

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
	private final Map<TransportId, Map<ContactId, Integer>> connections;
	private final Map<ContactId, Integer> contactCounts;

	@Inject
	ConnectionRegistryImpl(EventBus eventBus) {
		this.eventBus = eventBus;
		connections = new HashMap<TransportId, Map<ContactId, Integer>>();
		contactCounts = new HashMap<ContactId, Integer>();
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
			Map<ContactId, Integer> m = connections.get(t);
			if (m == null) {
				m = new HashMap<ContactId, Integer>();
				connections.put(t, m);
			}
			Integer count = m.get(c);
			if (count == null) m.put(c, 1);
			else m.put(c, count + 1);
			count = contactCounts.get(c);
			if (count == null) {
				firstConnection = true;
				contactCounts.put(c, 1);
			} else {
				contactCounts.put(c, count + 1);
			}
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
			Map<ContactId, Integer> m = connections.get(t);
			if (m == null) throw new IllegalArgumentException();
			Integer count = m.remove(c);
			if (count == null) throw new IllegalArgumentException();
			if (count == 1) {
				if (m.isEmpty()) connections.remove(t);
			} else {
				m.put(c, count - 1);
			}
			count = contactCounts.get(c);
			if (count == null) throw new IllegalArgumentException();
			if (count == 1) {
				lastConnection = true;
				contactCounts.remove(c);
			} else {
				contactCounts.put(c, count - 1);
			}
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
			Map<ContactId, Integer> m = connections.get(t);
			if (m == null) return Collections.emptyList();
			List<ContactId> ids = new ArrayList<ContactId>(m.keySet());
			if (LOG.isLoggable(INFO))
				LOG.info(ids.size() + " contacts connected");
			return ids;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean isConnected(ContactId c, TransportId t) {
		lock.lock();
		try {
			Map<ContactId, Integer> m = connections.get(t);
			return m != null && m.containsKey(c);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean isConnected(ContactId c) {
		lock.lock();
		try {
			return contactCounts.containsKey(c);
		} finally {
			lock.unlock();
		}
	}
}
