package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.keyagreement.KeyAgreementConnection;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementWaitingEvent;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.system.Clock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

@NotNullByDefault
@ThreadSafe
class ConnectionChooserImpl implements ConnectionChooser {

	private static final Logger LOG =
			Logger.getLogger(ConnectionChooserImpl.class.getName());

	private final EventBus eventBus;
	private final Clock clock;
	private final Object lock = new Object();
	private final List<KeyAgreementConnection> connections =
			new ArrayList<>(); // Locking: lock
	private boolean stopped = false; // Locking: lock

	@Inject
	ConnectionChooserImpl(EventBus eventBus, Clock clock) {
		this.eventBus = eventBus;
		this.clock = clock;
	}

	@Override
	public void addConnection(KeyAgreementConnection conn) {
		boolean close = false;
		synchronized (lock) {
			if (stopped) {
				// Already stopped, close the connection
				close = true;
			} else {
				connections.add(conn);
				lock.notifyAll();
			}
		}
		if (close) tryToClose(conn.getConnection());
	}

	@Nullable
	@Override
	public KeyAgreementConnection chooseConnection(boolean alice, long timeout)
			throws InterruptedException {
		if (alice) return chooseConnectionAlice(timeout);
		else return chooseConnectionBob(timeout);
	}

	@Nullable
	private KeyAgreementConnection chooseConnectionAlice(long timeout)
			throws InterruptedException {
		LOG.info("Choosing connection for Alice");
		long now = clock.currentTimeMillis();
		long end = now + timeout;
		KeyAgreementConnection chosen;
		synchronized (lock) {
			// Wait until we're stopped, a connection is added, or we time out
			while (!stopped && connections.isEmpty() && now < end) {
				lock.wait(end - now);
				now = clock.currentTimeMillis();
			}
			if (connections.isEmpty()) {
				LOG.info("No suitable connection for Alice");
				return null;
			}
			// Choose the first connection
			chosen = connections.remove(0);
		}
		if (LOG.isLoggable(INFO))
			LOG.info("Choosing " + chosen.getTransportId());
		return chosen;
	}

	@Nullable
	private KeyAgreementConnection chooseConnectionBob(long timeout)
			throws InterruptedException {
		LOG.info("Choosing connection for Bob");
		// Bob waits here for Alice to scan his QR code, determine her role,
		// choose a connection and send her key
		eventBus.broadcast(new KeyAgreementWaitingEvent());
		long now = clock.currentTimeMillis();
		long end = now + timeout;
		synchronized (lock) {
			while (!stopped && now < end) {
				// Check whether any connection has data available
				Iterator<KeyAgreementConnection> it = connections.iterator();
				while (it.hasNext()) {
					KeyAgreementConnection c = it.next();
					try {
						int available = c.getConnection().getReader()
								.getInputStream().available();
						if (available > 0) {
							if (LOG.isLoggable(INFO))
								LOG.info("Choosing " + c.getTransportId());
							it.remove();
							return c;
						}
					} catch (IOException e) {
						if (LOG.isLoggable(WARNING))
							LOG.log(WARNING, e.toString(), e);
						tryToClose(c.getConnection());
						it.remove();
					}
				}
				// Wait for 1 second before checking again
				lock.wait(Math.min(1000, end - now));
				now = clock.currentTimeMillis();
			}
		}
		LOG.info("No suitable connection for Bob");
		return null;
	}

	@Override
	public void stop() {
		List<KeyAgreementConnection> unused;
		synchronized (lock) {
			stopped = true;
			unused = new ArrayList<>(connections);
			connections.clear();
			lock.notifyAll();
		}
		if (LOG.isLoggable(INFO))
			LOG.info("Closing " + unused.size() + " unused connections");
		for (KeyAgreementConnection c : unused) tryToClose(c.getConnection());
	}

	private void tryToClose(DuplexTransportConnection conn) {
		try {
			conn.getReader().dispose(false, true);
			conn.getWriter().dispose(false);
		} catch (IOException e) {
			if (LOG.isLoggable(INFO)) LOG.info(e.toString());
		}
	}
}
