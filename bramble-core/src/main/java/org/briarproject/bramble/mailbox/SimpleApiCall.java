package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.mailbox.MailboxApi.ApiException;
import org.briarproject.bramble.mailbox.MailboxApi.TolerableFailureException;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

/**
 * Convenience class for making simple API calls that don't return values.
 */
@NotNullByDefault
class SimpleApiCall implements ApiCall {

	private static final Logger LOG = getLogger(SimpleApiCall.class.getName());

	private final Attempt attempt;

	SimpleApiCall(Attempt attempt) {
		this.attempt = attempt;
	}

	@Override
	public boolean callApi() {
		try {
			attempt.tryToCallApi();
			return false; // Succeeded, don't retry
		} catch (IOException | ApiException e) {
			logException(LOG, WARNING, e);
			return true; // Failed, retry with backoff
		} catch (TolerableFailureException e) {
			logException(LOG, INFO, e);
			return false; // Failed tolerably, don't retry
		}
	}

	interface Attempt {

		/**
		 * Makes a single attempt to call an API endpoint. If this method
		 * throws an {@link IOException} or an {@link ApiException}, the call
		 * will be retried. If it throws a {@link TolerableFailureException}
		 * or returns without throwing an exception, the call will not be
		 * retried.
		 */
		void tryToCallApi()
				throws IOException, ApiException, TolerableFailureException;
	}
}
