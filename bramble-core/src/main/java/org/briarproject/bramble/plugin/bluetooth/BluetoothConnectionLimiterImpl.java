package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.sync.event.CloseSyncConnectionsEvent;

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

	private final Object lock = new Object();
	@GuardedBy("lock")
	private final List<DuplexTransportConnection> connections =
			new LinkedList<>();
	@GuardedBy("lock")
	private int limitingInProgress = 0;

	BluetoothConnectionLimiterImpl(EventBus eventBus) {
		this.eventBus = eventBus;
	}

	@Override
	public void startLimiting() {
		synchronized (lock) {
			limitingInProgress++;
		}
		LOG.info("Limiting started");
		eventBus.broadcast(new CloseSyncConnectionsEvent(ID));
	}

	@Override
	public void endLimiting() {
		synchronized (lock) {
			limitingInProgress--;
			if (limitingInProgress < 0) {
				throw new IllegalStateException();
			}
		}
		LOG.info("Limiting ended");
	}

	@Override
	public boolean canOpenContactConnection() {
		synchronized (lock) {
			if (limitingInProgress > 0) {
				LOG.info("Can't open contact connection while limiting");
				return false;
			} else {
				LOG.info("Can open contact connection");
				return true;
			}
		}
	}

	@Override
	public void connectionOpened(DuplexTransportConnection conn) {
		synchronized (lock) {
			connections.add(conn);
			if (LOG.isLoggable(INFO)) {
				LOG.info("Connection opened, " + connections.size() + " open");
			}
		}
	}

	@Override
	public void connectionClosed(DuplexTransportConnection conn) {
		synchronized (lock) {
			connections.remove(conn);
			if (LOG.isLoggable(INFO)) {
				LOG.info("Connection closed, " + connections.size() + " open");
			}
		}
	}

	@Override
	public void allConnectionsClosed() {
		synchronized (lock) {
			connections.clear();
			LOG.info("All connections closed");
		}
	}
}
