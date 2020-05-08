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
import javax.inject.Inject;

import static java.lang.Math.min;
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
	private int connectionLimit = 1;
	@GuardedBy("lock")
	private long timeOfLastAttempt = 0,
			attemptInterval = MIN_ATTEMPT_INTERVAL_MS;

	@Inject
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
				LOG.info("Refusing contact connection during key agreement");
				return false;
			} else {
				long now = clock.currentTimeMillis();
				return isContactConnectionAllowedByLimit(now);
			}
		}
	}

	@Override
	public boolean contactConnectionOpened(DuplexTransportConnection conn,
			boolean incoming) {
		synchronized (lock) {
			if (keyAgreementInProgress) {
				LOG.info("Refusing contact connection during key agreement");
				return false;
			} else {
				long now = clock.currentTimeMillis();
				if (incoming || isContactConnectionAllowedByLimit(now)) {
					connections.add(new ConnectionRecord(conn, now));
					if (connections.size() > connectionLimit) {
						LOG.info("Attempting to raise connection limit");
						timeOfLastAttempt = now;
					}
					return true;
				} else {
					return false;
				}
			}
		}
	}

	@Override
	public void keyAgreementConnectionOpened(DuplexTransportConnection conn) {
		synchronized (lock) {
			LOG.info("Accepting key agreement connection");
			connections.add(
					new ConnectionRecord(conn, clock.currentTimeMillis()));
		}
	}

	@Override
	public void connectionClosed(DuplexTransportConnection conn,
			boolean exception) {
		synchronized (lock) {
			int numConnections = connections.size();
			Iterator<ConnectionRecord> it = connections.iterator();
			while (it.hasNext()) {
				if (it.next().connection == conn) {
					it.remove();
					if (exception && numConnections > connectionLimit) {
						connectionFailedAboveLimit();
					}
					break;
				}
			}
			if (LOG.isLoggable(INFO))
				LOG.info("Connection closed, " + connections.size() + " open");
		}
	}

	@Override
	public void bluetoothDisabled() {
		synchronized (lock) {
			connections.clear();
			LOG.info("Bluetooth disabled");
		}
	}

	@GuardedBy("lock")
	private boolean isContactConnectionAllowedByLimit(long now) {
		considerRaisingConnectionLimit(now);
		if (connections.size() > connectionLimit) {
			LOG.info("Refusing contact connection, above limit");
			return false;
		} else if (connections.size() < connectionLimit) {
			LOG.info("Allowing contact connection, below limit");
			return true;
		} else if (now - timeOfLastAttempt >= attemptInterval) {
			LOG.info("Allowing contact connection, at limit");
			return true;
		} else {
			LOG.info("Refusing contact connection, at limit");
			return false;
		}
	}

	@GuardedBy("lock")
	private void considerRaisingConnectionLimit(long now) {
		int stable = 0;
		for (ConnectionRecord rec : connections) {
			if (now - rec.timeOpened >= STABILITY_PERIOD_MS) stable++;
		}
		if (stable > connectionLimit) {
			LOG.info("Raising connection limit");
			connectionLimit++;
			attemptInterval = MIN_ATTEMPT_INTERVAL_MS;
		}
		if (LOG.isLoggable(INFO)) {
			LOG.info(stable + " connections are stable, limit is "
					+ connectionLimit);
		}
	}

	@GuardedBy("lock")
	private void connectionFailedAboveLimit() {
		long now = clock.currentTimeMillis();
		if (now - timeOfLastAttempt < STABILITY_PERIOD_MS) {
			LOG.info("Connection failed above limit, increasing interval");
			attemptInterval = min(attemptInterval * 2, MAX_ATTEMPT_INTERVAL_MS);
		}
	}

	private static final class ConnectionRecord {

		private final DuplexTransportConnection connection;
		private final long timeOpened;

		private ConnectionRecord(DuplexTransportConnection connection,
				long timeOpened) {
			this.connection = connection;
			this.timeOpened = timeOpened;
		}
	}
}
