package org.briarproject.transport;

import static java.util.logging.Level.INFO;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.transport.ConnectionListener;
import org.briarproject.api.transport.ConnectionRegistry;

class ConnectionRegistryImpl implements ConnectionRegistry {

	private static final Logger LOG =
			Logger.getLogger(ConnectionRegistryImpl.class.getName());

	// Locking: this
	private final Map<TransportId, Map<ContactId, Integer>> connections;
	// Locking: this
	private final Map<ContactId, Integer> contactCounts;
	private final List<ConnectionListener> listeners;

	ConnectionRegistryImpl() {
		connections = new HashMap<TransportId, Map<ContactId, Integer>>();
		contactCounts = new HashMap<ContactId, Integer>();
		listeners = new CopyOnWriteArrayList<ConnectionListener>();
	}

	public void addListener(ConnectionListener c) {
		listeners.add(c);
	}

	public void removeListener(ConnectionListener c) {
		listeners.remove(c);
	}

	public void registerConnection(ContactId c, TransportId t) {
		if(LOG.isLoggable(INFO)) LOG.info("Connection registered");
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
			if(LOG.isLoggable(INFO)) LOG.info("Contact connected");
			for(ConnectionListener l : listeners) l.contactConnected(c);
		}
	}

	public void unregisterConnection(ContactId c, TransportId t) {
		if(LOG.isLoggable(INFO)) LOG.info("Connection unregistered");
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
			if(LOG.isLoggable(INFO)) LOG.info("Contact disconnected");
			for(ConnectionListener l : listeners) l.contactDisconnected(c);
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
