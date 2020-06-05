package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.sync.event.CloseSyncConnectionsEvent;
import org.briarproject.bramble.api.system.Clock;

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
	private final List<DuplexTransportConnection> connections =
			new LinkedList<>();
	@GuardedBy("lock")
	private boolean keyAgreementInProgress = false;
	@GuardedBy("lock")
	private int connectionLimit = 2;
	@GuardedBy("lock")
	private long timeOfLastChange = 0;

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
			connections.add(conn);
			timeOfLastChange = now;
			if (LOG.isLoggable(INFO)) {
				LOG.info("Connection opened, " + connections.size() + " open");
			}
		}
	}

	@Override
	public void connectionClosed(DuplexTransportConnection conn,
			boolean exception) {
		synchronized (lock) {
			long now = clock.currentTimeMillis();
			if (exception) LOG.info("Connection failed");
			countStableConnections(now);
			connections.remove(conn);
			timeOfLastChange = now;
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
			timeOfLastChange = now;
			LOG.info("All connections closed");
		}
	}

	@GuardedBy("lock")
	private void countStableConnections(long now) {
		if (now - timeOfLastChange >= STABILITY_PERIOD_MS &&
				connections.size() > connectionLimit) {
			connectionLimit = connections.size();
			if (LOG.isLoggable(INFO)) {
				LOG.info("Raising connection limit to " + connectionLimit);
			}
		}
	}
}
