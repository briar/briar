package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.ThreadSafe;

import static java.util.concurrent.TimeUnit.MINUTES;

@ThreadSafe
@NotNullByDefault
interface TorReachabilityMonitor {

	/**
	 * How long the Tor plugin needs to be continuously
	 * {@link Plugin.State#ACTIVE active} before we assume our contacts can
	 * reach our hidden service.
	 */
	long REACHABILITY_PERIOD_MS = MINUTES.toMillis(10);

	/**
	 * Starts the monitor.
	 */
	void start();

	/**
	 * Destroys the monitor.
	 */
	void destroy();

	/**
	 * Adds an observer that will be called when our Tor hidden service becomes
	 * reachable. If our hidden service is already reachable, the observer is
	 * called immediately.
	 * <p>
	 * Observers are removed after being called, or when the monitor is
	 * {@link #destroy() destroyed}.
	 */
	void addOneShotObserver(TorReachabilityObserver o);

	/**
	 * Removes an observer that was added via
	 * {@link #addOneShotObserver(TorReachabilityObserver)}.
	 */
	void removeObserver(TorReachabilityObserver o);

	interface TorReachabilityObserver {

		void onTorReachable();
	}
}
