package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.nullsafety.NotNullByDefault;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;

@NotNullByDefault
interface MailboxApiCaller {

	/**
	 * The minimum interval between retries in milliseconds.
	 */
	long MIN_RETRY_INTERVAL_MS = MINUTES.toMillis(1);

	/**
	 * The maximum interval between retries in milliseconds.
	 */
	long MAX_RETRY_INTERVAL_MS = DAYS.toMillis(1);

	/**
	 * Asynchronously calls the given API call on the {@link IoExecutor},
	 * automatically retrying at increasing intervals until the API call
	 * returns false or retries are cancelled.
	 * <p>
	 * This method is safe to call while holding a lock.
	 *
	 * @return A {@link Cancellable} that can be used to cancel any future
	 * retries.
	 */
	Cancellable retryWithBackoff(ApiCall apiCall);
}
