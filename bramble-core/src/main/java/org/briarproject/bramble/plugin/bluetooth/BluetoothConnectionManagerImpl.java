package org.briarproject.bramble.plugin.bluetooth;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;

class BluetoothConnectionManagerImpl implements BluetoothConnectionManager {

	private static final int MAX_OPEN_CONNECTIONS = 5;

	private static final Logger LOG =
			Logger.getLogger(BluetoothConnectionManagerImpl.class.getName());

	private final AtomicInteger openConnections = new AtomicInteger(0);

	@Override
	public boolean canOpenConnection() {
		int open = openConnections.get();
		if (LOG.isLoggable(INFO))
			LOG.info(open + " open connections");
		return open < MAX_OPEN_CONNECTIONS;
	}

	@Override
	public boolean connectionOpened() {
		int open = openConnections.incrementAndGet();
		if (LOG.isLoggable(INFO))
			LOG.info("Connection opened, " + open + " open");
		return open <= MAX_OPEN_CONNECTIONS;
	}

	@Override
	public void connectionClosed() {
		int open = openConnections.decrementAndGet();
		if (LOG.isLoggable(INFO))
			LOG.info("Connection closed, " + open + " open");
	}

	@Override
	public void allConnectionsClosed() {
		LOG.info("All connections closed");
		openConnections.set(0);
	}
}
