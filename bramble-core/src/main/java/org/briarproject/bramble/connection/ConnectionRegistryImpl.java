package org.briarproject.bramble.connection;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.connection.ConnectionRegistry;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.event.ConnectionClosedEvent;
import org.briarproject.bramble.api.plugin.event.ConnectionOpenedEvent;
import org.briarproject.bramble.api.plugin.event.ContactConnectedEvent;
import org.briarproject.bramble.api.plugin.event.ContactDisconnectedEvent;
import org.briarproject.bramble.api.rendezvous.event.RendezvousConnectionClosedEvent;
import org.briarproject.bramble.api.rendezvous.event.RendezvousConnectionOpenedEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.Collections.emptyList;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;

@ThreadSafe
@NotNullByDefault
class ConnectionRegistryImpl implements ConnectionRegistry {

	private static final Logger LOG =
			getLogger(ConnectionRegistryImpl.class.getName());

	private final EventBus eventBus;
	private final Map<TransportId, List<TransportId>> betterTransports;
	private final Map<TransportId, List<TransportId>> worseTransports;

	private final Object lock = new Object();
	@GuardedBy("lock")
	private final Map<ContactId, List<ConnectionRecord>> contactConnections;
	@GuardedBy("lock")
	private final Set<PendingContactId> connectedPendingContacts;

	@Inject
	ConnectionRegistryImpl(EventBus eventBus, PluginConfig pluginConfig) {
		this.eventBus = eventBus;
		betterTransports = new HashMap<>();
		worseTransports = new HashMap<>();
		initTransportPreferences(pluginConfig.getTransportPreferences());
		contactConnections = new HashMap<>();
		connectedPendingContacts = new HashSet<>();
	}

	private void initTransportPreferences(
			List<Pair<TransportId, TransportId>> prefs) {
		for (Pair<TransportId, TransportId> pair : prefs) {
			TransportId better = pair.getFirst();
			TransportId worse = pair.getSecond();
			List<TransportId> betterList = betterTransports.get(worse);
			if (betterList == null) {
				betterList = new ArrayList<>();
				betterTransports.put(worse, betterList);
			}
			betterList.add(better);
			List<TransportId> worseList = worseTransports.get(better);
			if (worseList == null) {
				worseList = new ArrayList<>();
				worseTransports.put(better, worseList);
			}
			worseList.add(worse);
		}
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
			List<ConnectionRecord> recs = contactConnections.get(c);
			if (recs == null) {
				recs = new ArrayList<>();
				contactConnections.put(c, recs);
			}
			if (recs.isEmpty()) firstConnection = true;
			recs.add(new ConnectionRecord(t));
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
			List<ConnectionRecord> recs = contactConnections.get(c);
			if (recs == null || !recs.remove(new ConnectionRecord(t)))
				throw new IllegalArgumentException();
			if (recs.isEmpty()) lastConnection = true;
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
			List<ContactId> contactIds = new ArrayList<>();
			for (Entry<ContactId, List<ConnectionRecord>> e :
					contactConnections.entrySet()) {
				for (ConnectionRecord rec : e.getValue()) {
					if (rec.transportId.equals(t)) {
						contactIds.add(e.getKey());
						break;
					}
				}
			}
			if (LOG.isLoggable(INFO)) {
				LOG.info(contactIds.size() + " contacts connected: " + t);
			}
			return contactIds;
		}
	}

	@Override
	public Collection<ContactId> getConnectedOrPreferredContacts(
			TransportId t) {
		synchronized (lock) {
			List<TransportId> better = betterTransports.get(t);
			if (better == null) better = emptyList();
			List<ContactId> contactIds = new ArrayList<>();
			for (Entry<ContactId, List<ConnectionRecord>> e :
					contactConnections.entrySet()) {
				for (ConnectionRecord rec : e.getValue()) {
					if (rec.transportId.equals(t) ||
							better.contains(rec.transportId)) {
						contactIds.add(e.getKey());
						break;
					}
				}
			}
			if (LOG.isLoggable(INFO)) {
				LOG.info(contactIds.size()
						+ " contacts connected or preferred: " + t);
			}
			return contactIds;
		}
	}

	@Override
	public boolean isConnected(ContactId c, TransportId t) {
		synchronized (lock) {
			List<ConnectionRecord> recs = contactConnections.get(c);
			if (recs == null) return false;
			for (ConnectionRecord rec : recs) {
				if (rec.transportId.equals(t)) return true;
			}
			return false;
		}
	}

	@Override
	public boolean isConnected(ContactId c) {
		synchronized (lock) {
			return contactConnections.containsKey(c);
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

	private static class ConnectionRecord {

		private final TransportId transportId;

		private ConnectionRecord(TransportId transportId) {
			this.transportId = transportId;
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof ConnectionRecord) {
				ConnectionRecord rec = (ConnectionRecord) o;
				return transportId.equals(rec.transportId);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return transportId.hashCode();
		}
	}
}
