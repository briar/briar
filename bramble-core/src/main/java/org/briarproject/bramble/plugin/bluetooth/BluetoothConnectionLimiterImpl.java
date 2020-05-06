package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.sync.event.CloseSyncConnectionsEvent;
import org.briarproject.bramble.api.system.Clock;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.ID;
import static org.briarproject.bramble.util.LogUtils.logException;

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
				LOG.info("Can't open contact connection during key agreement");
				return false;
			}
			considerRaisingConnectionLimit(clock.currentTimeMillis());
			if (connections.size() >= connectionLimit) {
				LOG.info("Can't open contact connection due to limit");
				return false;
			} else {
				LOG.info("Can open contact connection");
				return true;
			}
		}
	}

	@Override
	public boolean contactConnectionOpened(DuplexTransportConnection conn) {
		boolean accept = true;
		synchronized (lock) {
			if (keyAgreementInProgress) {
				LOG.info("Refusing contact connection during key agreement");
				accept = false;
			} else {
				long now = clock.currentTimeMillis();
				considerRaisingConnectionLimit(now);
				if (connections.size() > connectionLimit) {
					LOG.info("Refusing contact connection due to limit");
					accept = false;
				} else {
					LOG.info("Accepting contact connection");
					connections.add(new ConnectionRecord(conn, now));
				}
			}
		}
		if (!accept) tryToClose(conn);
		return accept;
	}

	@Override
	public void keyAgreementConnectionOpened(DuplexTransportConnection conn) {
		synchronized (lock) {
			LOG.info("Accepting key agreement connection");
			connections.add(
					new ConnectionRecord(conn, clock.currentTimeMillis()));
		}
	}

	private void tryToClose(DuplexTransportConnection conn) {
		try {
			conn.getWriter().dispose(false);
			conn.getReader().dispose(false, false);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
		}
	}

	@Override
	public void connectionClosed(DuplexTransportConnection conn,
			boolean exception) {
		synchronized (lock) {
			Iterator<ConnectionRecord> it = connections.iterator();
			while (it.hasNext()) {
				if (it.next().connection == conn) {
					it.remove();
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
	private void considerRaisingConnectionLimit(long now) {
		int stable = 0;
		for (ConnectionRecord rec : connections) {
			if (now - rec.timeOpened >= STABILITY_PERIOD_MS) stable++;
		}
		if (stable >= connectionLimit) {
			LOG.info("Raising connection limit");
			connectionLimit++;
		}
		if (LOG.isLoggable(INFO)) {
			LOG.info(stable + " connections are stable, limit is "
					+ connectionLimit);
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
