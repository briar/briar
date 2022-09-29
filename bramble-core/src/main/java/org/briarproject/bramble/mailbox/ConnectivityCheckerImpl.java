package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.nullsafety.NotNullByDefault;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
abstract class ConnectivityCheckerImpl implements ConnectivityChecker {

	/**
	 * If no more than this much time has elapsed since the last connectivity
	 * check succeeded, consider the result to be fresh and don't check again.
	 * <p>
	 * Package access for testing.
	 */
	static final long CONNECTIVITY_CHECK_FRESHNESS_MS = 10_000;

	private final Object lock = new Object();

	protected final Clock clock;
	private final MailboxApiCaller mailboxApiCaller;

	@GuardedBy("lock")
	private boolean destroyed = false;

	@GuardedBy("lock")
	@Nullable
	private Cancellable connectivityCheck = null;

	@GuardedBy("lock")
	private long lastConnectivityCheckSucceeded = 0;

	@GuardedBy("lock")
	private final List<ConnectivityObserver> connectivityObservers =
			new ArrayList<>();

	/**
	 * Creates an {@link ApiCall} for checking whether the mailbox is
	 * reachable. The {@link ApiCall} should call
	 * {@link #onConnectivityCheckSucceeded(long)} if the check succeeds.
	 */
	abstract ApiCall createConnectivityCheckTask(MailboxProperties properties);

	ConnectivityCheckerImpl(Clock clock, MailboxApiCaller mailboxApiCaller) {
		this.clock = clock;
		this.mailboxApiCaller = mailboxApiCaller;
	}

	@Override
	public void destroy() {
		synchronized (lock) {
			destroyed = true;
			connectivityObservers.clear();
			if (connectivityCheck != null) {
				connectivityCheck.cancel();
				connectivityCheck = null;
			}
		}
	}

	@Override
	public void checkConnectivity(MailboxProperties properties,
			ConnectivityObserver o) {
		boolean callNow = false;
		synchronized (lock) {
			if (destroyed) return;
			if (connectivityCheck == null) {
				// No connectivity check is running
				long now = clock.currentTimeMillis();
				if (now - lastConnectivityCheckSucceeded
						> CONNECTIVITY_CHECK_FRESHNESS_MS) {
					// The last connectivity check is stale, start a new one
					connectivityObservers.add(o);
					ApiCall task = createConnectivityCheckTask(properties);
					connectivityCheck = mailboxApiCaller.retryWithBackoff(task);
				} else {
					// The last connectivity check is fresh
					callNow = true;
				}
			} else {
				// A connectivity check is running, wait for it to succeed
				connectivityObservers.add(o);
			}
		}
		if (callNow) o.onConnectivityCheckSucceeded();
	}

	protected void onConnectivityCheckSucceeded(long now) {
		List<ConnectivityObserver> observers;
		synchronized (lock) {
			if (destroyed) return;
			connectivityCheck = null;
			lastConnectivityCheckSucceeded = now;
			observers = new ArrayList<>(connectivityObservers);
			connectivityObservers.clear();
		}
		for (ConnectivityObserver o : observers) {
			o.onConnectivityCheckSucceeded();
		}
	}

	@Override
	public void removeObserver(ConnectivityObserver o) {
		synchronized (lock) {
			if (destroyed) return;
			connectivityObservers.remove(o);
			if (connectivityObservers.isEmpty() && connectivityCheck != null) {
				connectivityCheck.cancel();
				connectivityCheck = null;
			}
		}
	}
}
