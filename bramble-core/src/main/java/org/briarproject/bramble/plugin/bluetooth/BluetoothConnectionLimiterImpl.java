package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
@ThreadSafe
class BluetoothConnectionLimiterImpl implements BluetoothConnectionLimiter {

	private static final Logger LOG =
			Logger.getLogger(BluetoothConnectionLimiterImpl.class.getName());

	private final Object lock = new Object();
	// The following are locking: lock
	private final LinkedList<DuplexTransportConnection> connections =
			new LinkedList<>();
	private boolean keyAgreementInProgress = false;

	@Override
	public void keyAgreementStarted() {
		List<DuplexTransportConnection> close;
		synchronized (lock) {
			keyAgreementInProgress = true;
			close = new ArrayList<>(connections);
			connections.clear();
		}
		if (LOG.isLoggable(INFO)) {
			LOG.info("Key agreement started, closing " + close.size() +
					" connections");
		}
		for (DuplexTransportConnection conn : close) tryToClose(conn);
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
				LOG.info("Accepting contact connection");
				connections.add(conn);
			}
		}
		if (!accept) tryToClose(conn);
		return accept;
	}

	@Override
	public void keyAgreementConnectionOpened(DuplexTransportConnection conn) {
		synchronized (lock) {
			LOG.info("Accepting key agreement connection");
			connections.add(conn);
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
	public void connectionClosed(DuplexTransportConnection conn) {
		synchronized (lock) {
			connections.remove(conn);
			if (LOG.isLoggable(INFO))
				LOG.info("Connection closed, " + connections.size() + " open");
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
