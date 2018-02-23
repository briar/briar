package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

import java.io.IOException;
import java.util.LinkedList;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

@NotNullByDefault
@ThreadSafe
class BluetoothConnectionManagerImpl implements BluetoothConnectionManager {

	private static final int MAX_OPEN_CONNECTIONS = 5;

	private static final Logger LOG =
			Logger.getLogger(BluetoothConnectionManagerImpl.class.getName());

	private final Object lock = new Object();
	private final LinkedList<DuplexTransportConnection> connections =
			new LinkedList<>(); // Locking: lock

	@Override
	public boolean canOpenConnection() {
		synchronized (lock) {
			int open = connections.size();
			if (LOG.isLoggable(INFO)) LOG.info(open + " open connections");
			return open < MAX_OPEN_CONNECTIONS;
		}
	}

	@Override
	public boolean connectionOpened(DuplexTransportConnection conn,
			boolean isForKeyExchange) {
		DuplexTransportConnection close = null;
		synchronized (lock) {
			int open = connections.size();
			boolean accept = isForKeyExchange || open < MAX_OPEN_CONNECTIONS;
			if (accept) {
				if (LOG.isLoggable(INFO))
					LOG.info("Accepting connection, " + (open + 1) + " open");
				connections.add(conn);
				if (open == MAX_OPEN_CONNECTIONS) {
					LOG.info("Closing old connection to stay within limit");
					close = connections.poll();
				}
			} else {
				if (LOG.isLoggable(INFO))
					LOG.info("Refusing connection, " + open + " open");
				close = conn;
			}
		}
		if (close != null) tryToClose(close);
		return close != conn;
	}

	private void tryToClose(DuplexTransportConnection conn) {
		try {
			conn.getWriter().dispose(false);
			conn.getReader().dispose(false, false);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	@Override
	public void connectionClosed(DuplexTransportConnection conn) {
		synchronized (lock) {
			connections.remove(conn);
			if (LOG.isLoggable(INFO)) {
				int open = connections.size();
				LOG.info("Connection closed, " + open + " open");
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
