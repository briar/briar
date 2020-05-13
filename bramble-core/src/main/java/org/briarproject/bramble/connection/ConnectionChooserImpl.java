package org.briarproject.bramble.connection;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.sync.Priority;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.Bytes.compare;

@NotNullByDefault
class ConnectionChooserImpl implements ConnectionChooser {

	private static final Logger LOG =
			getLogger(ConnectionChooserImpl.class.getName());

	private final Object lock = new Object();
	@GuardedBy("lock")
	private final Map<Key, Value> bestConnections = new HashMap<>();

	@Inject
	ConnectionChooserImpl() {
	}

	@Override
	public void addConnection(ContactId c, TransportId t,
			DuplexSyncConnection conn, Priority p) {
		DuplexSyncConnection close = null;
		synchronized (lock) {
			Key k = new Key(c, t);
			Value best = bestConnections.get(k);
			if (best == null) {
				bestConnections.put(k, new Value(conn, p));
			} else if (compare(p.getNonce(), best.priority.getNonce()) > 0) {
				LOG.info("Found a better connection");
				close = best.connection;
				bestConnections.put(k, new Value(conn, p));
			} else {
				LOG.info("Already have a better connection");
				close = conn;
			}
		}
		if (close != null) close.interruptOutgoingSession();
	}

	@Override
	public void removeConnection(ContactId c, TransportId t,
			DuplexSyncConnection conn) {
		synchronized (lock) {
			Key k = new Key(c, t);
			Value best = bestConnections.get(k);
			if (best.connection == conn) bestConnections.remove(k);
		}
	}

	private static class Key {

		private final ContactId contactId;
		private final TransportId transportId;

		private Key(ContactId contactId, TransportId transportId) {
			this.contactId = contactId;
			this.transportId = transportId;
		}

		@Override
		public int hashCode() {
			return contactId.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof Key) {
				Key k = (Key) o;
				return contactId.equals(k.contactId) &&
						transportId.equals(k.transportId);
			} else {
				return false;
			}
		}
	}

	private static class Value {

		private final DuplexSyncConnection connection;
		private final Priority priority;

		private Value(DuplexSyncConnection connection, Priority priority) {
			this.connection = connection;
			this.priority = priority;
		}
	}
}
