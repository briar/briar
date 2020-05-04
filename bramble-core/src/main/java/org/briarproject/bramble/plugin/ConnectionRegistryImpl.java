package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.Multiset;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.bramble.api.plugin.ConnectionStatus;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.event.ConnectionClosedEvent;
import org.briarproject.bramble.api.plugin.event.ConnectionOpenedEvent;
import org.briarproject.bramble.api.plugin.event.ConnectionStatusChangedEvent;
import org.briarproject.bramble.api.rendezvous.event.RendezvousConnectionClosedEvent;
import org.briarproject.bramble.api.rendezvous.event.RendezvousConnectionOpenedEvent;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.Scheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.ConnectionStatus.CONNECTED;
import static org.briarproject.bramble.api.plugin.ConnectionStatus.DISCONNECTED;
import static org.briarproject.bramble.api.plugin.ConnectionStatus.RECENTLY_CONNECTED;

@ThreadSafe
@NotNullByDefault
class ConnectionRegistryImpl implements ConnectionRegistry {

	private static final Logger LOG =
			getLogger(ConnectionRegistryImpl.class.getName());

	private static final long RECENTLY_CONNECTED_MS = MINUTES.toMillis(1);
	private static final long EXPIRY_INTERVAL_MS = SECONDS.toMillis(10);

	private final EventBus eventBus;
	private final Clock clock;

	private final Object lock = new Object();
	@GuardedBy("lock")
	private final Map<TransportId, Multiset<ContactId>> contactConnections;
	@GuardedBy("lock")
	private final Map<ContactId, Counter> contactCounts;
	@GuardedBy("lock")
	private final Set<PendingContactId> connectedPendingContacts;

	@Inject
	ConnectionRegistryImpl(EventBus eventBus, Clock clock,
			@Scheduler ScheduledExecutorService scheduler) {
		this.eventBus = eventBus;
		this.clock = clock;
		contactConnections = new HashMap<>();
		contactCounts = new HashMap<>();
		connectedPendingContacts = new HashSet<>();
		scheduler.scheduleWithFixedDelay(this::expireRecentConnections,
				EXPIRY_INTERVAL_MS, EXPIRY_INTERVAL_MS, MILLISECONDS);
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

			Counter counter = contactCounts.get(c);
			if (counter == null) {
				counter = new Counter();
				contactCounts.put(c, counter);
			}
			if (counter.connections == 0) {
				counter.disconnectedTime = 0;
				firstConnection = true;
			}
			counter.connections++;
		}
		eventBus.broadcast(new ConnectionOpenedEvent(c, t, incoming));
		if (firstConnection) {
			LOG.info("Contact connected");
			eventBus.broadcast(new ConnectionStatusChangedEvent(c, CONNECTED));
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

			Counter counter = contactCounts.get(c);
			if (counter == null || counter.connections == 0) {
				throw new IllegalArgumentException();
			}
			counter.connections--;
			if (counter.connections == 0) {
				counter.disconnectedTime = clock.currentTimeMillis();
				lastConnection = true;
			}
		}
		eventBus.broadcast(new ConnectionClosedEvent(c, t, incoming));
		if (lastConnection) {
			LOG.info("Contact disconnected");
			eventBus.broadcast(
					new ConnectionStatusChangedEvent(c, RECENTLY_CONNECTED));
		}
	}

	@Override
	public Collection<ContactId> getConnectedContacts(TransportId t) {
		synchronized (lock) {
			Multiset<ContactId> m = contactConnections.get(t);
			if (m == null) return emptyList();
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
	public ConnectionStatus getConnectionStatus(ContactId c) {
		synchronized (lock) {
			Counter counter = contactCounts.get(c);
			if (counter == null) return DISCONNECTED;
			return counter.connections > 0 ? CONNECTED : RECENTLY_CONNECTED;
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

	@Scheduler
	private void expireRecentConnections() {
		long now = clock.currentTimeMillis();
		List<ContactId> disconnected = new ArrayList<>();
		synchronized (lock) {
			Iterator<Entry<ContactId, Counter>> it =
					contactCounts.entrySet().iterator();
			while (it.hasNext()) {
				Entry<ContactId, Counter> e = it.next();
				if (e.getValue().isExpired(now)) {
					disconnected.add(e.getKey());
					it.remove();
				}
			}
		}
		for (ContactId c : disconnected) {
			eventBus.broadcast(
					new ConnectionStatusChangedEvent(c, DISCONNECTED));
		}
	}

	private static class Counter {

		private int connections = 0;
		private long disconnectedTime = 0;

		private boolean isExpired(long now) {
			return connections == 0 &&
					now - disconnectedTime > RECENTLY_CONNECTED_MS;
		}
	}
}
