package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.sync.event.CloseSyncConnectionsEvent;
import org.briarproject.bramble.api.system.Clock;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.ID;

@NotNullByDefault
@ThreadSafe
class BluetoothConnectionLimiterImpl implements BluetoothConnectionLimiter {

	private static final Logger LOG =
			getLogger(BluetoothConnectionLimiterImpl.class.getName());

	private final EventBus eventBus;
	private final Clock clock;

	private final Object lock = new Object();
	@GuardedBy("lock")
	private final List<ConnectionRecord> connections = new LinkedList<>();
	@GuardedBy("lock")
	private boolean keyAgreementInProgress = false;
	@GuardedBy("lock")
	private int connectionLimit = 2;

	BluetoothConnectionLimiterImpl(EventBus eventBus, Clock clock) {
		this.eventBus = eventBus;
		this.clock = clock;
	}

	@Override
	public void keyAgreementStarted() {
		synchronized (lock) {
			keyAgreementInProgress = true;
		}
		LOG.info("Key agreement started");
		eventBus.broadcast(new CloseSyncConnectionsEvent(ID));
	}

	@Override
	public void keyAgreementEnded() {
		synchronized (lock) {
			keyAgreementInProgress = false;
		}
		LOG.info("Key agreement ended");
	}

	@Override
	public boolean canOpenContactConnection() {
		synchronized (lock) {
			if (keyAgreementInProgress) {
				LOG.info("Can't open contact connection during key agreement");
				return false;
			}
			long now = clock.currentTimeMillis();
			countStableConnections(now);
			if (connections.size() < connectionLimit) {
				LOG.info("Can open contact connection");
				return true;
			} else {
				LOG.info("Can't open contact connection due to limit");
				return false;
			}
		}
	}

	@Override
	public void connectionOpened(DuplexTransportConnection conn) {
		synchronized (lock) {
			long now = clock.currentTimeMillis();
			countStableConnections(now);
			connections.add(new ConnectionRecord(conn, now));
			if (LOG.isLoggable(INFO)) {
				LOG.info("Connection opened, " + connections.size() + " open");
			}
		}
	}

	@Override
	public void connectionClosed(DuplexTransportConnection conn) {
		synchronized (lock) {
			countStableConnections(clock.currentTimeMillis());
			Iterator<ConnectionRecord> it = connections.iterator();
			while (it.hasNext()) {
				if (it.next().conn == conn) {
					it.remove();
					break;
				}
			}
			if (LOG.isLoggable(INFO)) {
				LOG.info("Connection closed, " + connections.size() + " open");
			}
		}
	}

	@Override
	public void allConnectionsClosed() {
		synchronized (lock) {
			long now = clock.currentTimeMillis();
			countStableConnections(now);
			connections.clear();
			LOG.info("All connections closed");
		}
	}

	@GuardedBy("lock")
	private void countStableConnections(long now) {
		int stable = 0;
		for (ConnectionRecord rec : connections) {
			if (now - rec.timeOpened >= STABILITY_PERIOD_MS) stable++;
		}
		if (stable > connectionLimit) {
			connectionLimit = stable;
			if (LOG.isLoggable(INFO)) {
				LOG.info("Raising connection limit to " + connectionLimit);
			}
		}
	}

	private static class ConnectionRecord {

		private final DuplexTransportConnection conn;
		private final long timeOpened;

		private ConnectionRecord(DuplexTransportConnection conn,
				long timeOpened) {
			this.conn = conn;
			this.timeOpened = timeOpened;
		}
	}
}
