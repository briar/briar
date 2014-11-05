package org.briarproject.plugins;

import static java.util.logging.Level.INFO;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.event.ContactConnectedEvent;
import org.briarproject.api.event.ContactDisconnectedEvent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.plugins.ConnectionRegistry;

import com.google.inject.Inject;

class ConnectionRegistryImpl implements ConnectionRegistry {

	private static final Logger LOG =
			Logger.getLogger(ConnectionRegistryImpl.class.getName());

	private final EventBus eventBus;
	// Locking: this
	private final Map<TransportId, Map<ContactId, Integer>> connections;
	// Locking: this
	private final Map<ContactId, Integer> contactCounts;

	@Inject
	ConnectionRegistryImpl(EventBus eventBus) {
		this.eventBus = eventBus;
		connections = new HashMap<TransportId, Map<ContactId, Integer>>();
		contactCounts = new HashMap<ContactId, Integer>();
	}

	public void registerConnection(ContactId c, TransportId t) {
		LOG.info("Connection registered");
		boolean firstConnection = false;
		synchronized(this) {
			Map<ContactId, Integer> m = connections.get(t);
			if(m == null) {
				m = new HashMap<ContactId, Integer>();
				connections.put(t, m);
			}
			Integer count = m.get(c);
			if(count == null) m.put(c, 1);
			else m.put(c, count + 1);
			count = contactCounts.get(c);
			if(count == null) {
				firstConnection = true;
				contactCounts.put(c, 1);
			} else {
				contactCounts.put(c, count + 1);
			}
		}
		if(firstConnection) {
			LOG.info("Contact connected");
			eventBus.broadcast(new ContactConnectedEvent(c));
		}
	}

	public void unregisterConnection(ContactId c, TransportId t) {
		LOG.info("Connection unregistered");
		boolean lastConnection = false;
		synchronized(this) {
			Map<ContactId, Integer> m = connections.get(t);
			if(m == null) throw new IllegalArgumentException();
			Integer count = m.remove(c);
			if(count == null) throw new IllegalArgumentException();
			if(count == 1) {
				if(m.isEmpty()) connections.remove(t);
			} else {
				m.put(c, count - 1);
			}
			count = contactCounts.get(c);
			if(count == null) throw new IllegalArgumentException();
			if(count == 1) {
				lastConnection = true;
				contactCounts.remove(c);
			} else {
				contactCounts.put(c, count - 1);
			}
		}
		if(lastConnection) {
			LOG.info("Contact disconnected");
			eventBus.broadcast(new ContactDisconnectedEvent(c));
		}
	}

	public synchronized Collection<ContactId> getConnectedContacts(
			TransportId t) {
		Map<ContactId, Integer> m = connections.get(t);
		if(m == null) return Collections.emptyList();
		List<ContactId> ids = new ArrayList<ContactId>(m.keySet());
		if(LOG.isLoggable(INFO)) LOG.info(ids.size() + " contacts connected");
		return Collections.unmodifiableList(ids);
	}

	public synchronized boolean isConnected(ContactId c) {
		return contactCounts.containsKey(c);
	}
}
