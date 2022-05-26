package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.ThreadSafe;

/**
 * An interface for checking whether a mailbox is reachable.
 */
@ThreadSafe
@NotNullByDefault
interface ConnectivityChecker {

	/**
	 * Destroys the checker. Any current connectivity check is cancelled.
	 */
	void destroy();

	/**
	 * Starts a connectivity check if needed and calls the given observer when
	 * the check succeeds. If a check is already running then the observer is
	 * called when the check succeeds. If a connectivity check has recently
	 * succeeded then the observer is called immediately.
	 */
	void checkConnectivity(MailboxProperties properties,
			ConnectivityObserver o);

	interface ConnectivityObserver {
		void onConnectivityCheckSucceeded();
	}
}
