package org.briarproject.bramble.connection;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.connection.ConnectionRegistry;
import org.briarproject.bramble.api.connection.InterruptibleConnection;
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
import org.briarproject.bramble.api.sync.Priority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nullable;
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
	private final Map<TransportId, List<TransportId>> transportPrefs;

	private final Object lock = new Object();
	@GuardedBy("lock")
	private final Map<ContactId, List<ConnectionRecord>> contactConnections;
	@GuardedBy("lock")
	private final Set<PendingContactId> connectedPendingContacts;

	@Inject
	ConnectionRegistryImpl(EventBus eventBus, PluginConfig pluginConfig) {
		this.eventBus = eventBus;
		transportPrefs = pluginConfig.getTransportPreferences();
		contactConnections = new HashMap<>();
		connectedPendingContacts = new HashSet<>();
	}

	@Override
	public void registerIncomingConnection(ContactId c, TransportId t,
			InterruptibleConnection conn) {
		registerConnection(c, t, conn, true);
	}

	@Override
	public void registerOutgoingConnection(ContactId c, TransportId t,
			InterruptibleConnection conn, Priority priority) {
		registerConnection(c, t, conn, false);
		setPriority(c, t, conn, priority);
	}

	private void registerConnection(ContactId c, TransportId t,
			InterruptibleConnection conn, boolean incoming) {
		if (LOG.isLoggable(INFO)) {
			if (incoming) LOG.info("Incoming connection registered: " + t);
			else LOG.info("Outgoing connection registered: " + t);
		}
		boolean firstConnection;
		synchronized (lock) {
			List<ConnectionRecord> recs = contactConnections.get(c);
			if (recs == null) {
				recs = new ArrayList<>();
				contactConnections.put(c, recs);
			}
			firstConnection = recs.isEmpty();
			recs.add(new ConnectionRecord(t, conn));
		}
		eventBus.broadcast(new ConnectionOpenedEvent(c, t, incoming));
		if (firstConnection) {
			LOG.info("Contact connected");
			eventBus.broadcast(new ContactConnectedEvent(c));
		}
	}

	@Override
	public void setPriority(ContactId c, TransportId t,
			InterruptibleConnection conn, Priority priority) {
		if (LOG.isLoggable(INFO)) LOG.info("Setting connection priority: " + t);
		List<InterruptibleConnection> toInterrupt;
		boolean interruptNewConnection = false;
		synchronized (lock) {
			List<ConnectionRecord> recs = contactConnections.get(c);
			if (recs == null) throw new IllegalArgumentException();
			toInterrupt = new ArrayList<>(recs.size());
			for (ConnectionRecord rec : recs) {
				if (rec.conn == conn) {
					// Store the priority of this connection
					rec.priority = priority;
				} else if (rec.priority != null) {
					int compare = compareConnections(t, priority,
							rec.transportId, rec.priority);
					if (compare == -1) {
						// The old connection is better than the new one
						interruptNewConnection = true;
					} else if (compare == 1 && !rec.interrupted) {
						// The new connection is better than the old one
						toInterrupt.add(rec.conn);
						rec.interrupted = true;
					}
				}
			}
		}
		if (interruptNewConnection) {
			LOG.info("Interrupting new connection");
			conn.interruptOutgoingSession();
		}
		for (InterruptibleConnection old : toInterrupt) {
			LOG.info("Interrupting old connection");
			old.interruptOutgoingSession();
		}
	}

	private int compareConnections(TransportId tA, Priority pA, TransportId tB,
			Priority pB) {
		if (getBetterTransports(tA).contains(tB)) return -1;
		if (getBetterTransports(tB).contains(tA)) return 1;
		return tA.equals(tB) ? Bytes.compare(pA.getNonce(), pB.getNonce()) : 0;
	}

	private List<TransportId> getBetterTransports(TransportId t) {
		List<TransportId> better = transportPrefs.get(t);
		return better == null ? emptyList() : better;
	}

	@Override
	public void unregisterConnection(ContactId c, TransportId t,
			InterruptibleConnection conn, boolean incoming, boolean exception) {
		if (LOG.isLoggable(INFO)) {
			if (incoming) LOG.info("Incoming connection unregistered: " + t);
			else LOG.info("Outgoing connection unregistered: " + t);
		}
		boolean lastConnection;
		synchronized (lock) {
			List<ConnectionRecord> recs = contactConnections.get(c);
			if (recs == null || !recs.remove(new ConnectionRecord(t, conn)))
				throw new IllegalArgumentException();
			lastConnection = recs.isEmpty();
		}
		eventBus.broadcast(
				new ConnectionClosedEvent(c, t, incoming, exception));
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
	public Collection<ContactId> getConnectedOrBetterContacts(TransportId t) {
		synchronized (lock) {
			List<TransportId> better = getBetterTransports(t);
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
						+ " contacts connected or better: " + t);
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
			List<ConnectionRecord> recs = contactConnections.get(c);
			return recs != null && !recs.isEmpty();
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
		private final InterruptibleConnection conn;
		@GuardedBy("lock")
		@Nullable
		private Priority priority = null;
		@GuardedBy("lock")
		private boolean interrupted = false;

		private ConnectionRecord(TransportId transportId,
				InterruptibleConnection conn) {
			this.transportId = transportId;
			this.conn = conn;
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof ConnectionRecord) {
				return conn == ((ConnectionRecord) o).conn;
			} else {
				return false;
			}
		}

		@Override
		public int hashCode() {
			return conn.hashCode();
		}
	}
}
