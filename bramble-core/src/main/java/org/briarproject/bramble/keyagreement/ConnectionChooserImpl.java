package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.keyagreement.KeyAgreementConnection;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.system.Clock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.logging.Level.INFO;

@NotNullByDefault
@ThreadSafe
class ConnectionChooserImpl implements ConnectionChooser {

	private static final Logger LOG =
			Logger.getLogger(ConnectionChooserImpl.class.getName());

	private final Clock clock;
	private final Executor ioExecutor;
	private final Object lock = new Object();

	// The following are locking: lock
	private boolean stopped = false;
	private final Queue<KeyAgreementConnection> results = new LinkedList<>();

	@Inject
	ConnectionChooserImpl(Clock clock, @IoExecutor Executor ioExecutor) {
		this.clock = clock;
		this.ioExecutor = ioExecutor;
	}

	@Override
	public void submit(Callable<KeyAgreementConnection> task) {
		ioExecutor.execute(() -> {
			try {
				KeyAgreementConnection c = task.call();
				if (c != null) addResult(c);
			} catch (Exception e) {
				if (LOG.isLoggable(INFO)) LOG.info(e.toString());
			}
		});
	}

	@Nullable
	@Override
	public KeyAgreementConnection poll(long timeout)
			throws InterruptedException {
		long now = clock.currentTimeMillis();
		long end = now + timeout;
		synchronized (lock) {
			while (!stopped && results.isEmpty() && now < end) {
				lock.wait(end - now);
				now = clock.currentTimeMillis();
			}
			return results.poll();
		}
	}

	@Override
	public void stop() {
		List<KeyAgreementConnection> unused;
		synchronized (lock) {
			unused = new ArrayList<>(results);
			results.clear();
			stopped = true;
			lock.notifyAll();
		}
		if (LOG.isLoggable(INFO))
			LOG.info("Closing " + unused.size() + " unused connections");
		for (KeyAgreementConnection c : unused) tryToClose(c.getConnection());
	}

	private void addResult(KeyAgreementConnection c) {
		if (LOG.isLoggable(INFO))
			LOG.info("Got connection for " + c.getTransportId());
		boolean close = false;
		synchronized (lock) {
			if (stopped) {
				close = true;
			} else {
				results.add(c);
				lock.notifyAll();
			}
		}
		if (close) {
			LOG.info("Already stopped");
			tryToClose(c.getConnection());
		}
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
