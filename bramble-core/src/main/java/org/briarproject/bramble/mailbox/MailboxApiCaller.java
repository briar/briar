package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.Supplier;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.mailbox.MailboxApi.TolerableFailureException;

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
	 * Asynchronously calls the given supplier, automatically retrying at
	 * increasing intervals until the supplier returns false. The returned
	 * {@link Cancellable} can be used to cancel any future retries.
	 *
	 * @param supplier A wrapper for an API call. The supplier's
	 * {@link Supplier#get() get()} method will be called on the
	 * {@link IoExecutor}. It should return true if the API call needs to be
	 * retried, or false if the API call succeeded or
	 * {@link TolerableFailureException failed tolerably}.
	 */
	Cancellable retryWithBackoff(Supplier<Boolean> supplier);
}
