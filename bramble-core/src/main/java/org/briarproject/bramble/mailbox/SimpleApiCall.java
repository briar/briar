package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Supplier;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.mailbox.MailboxApi.ApiException;
import org.briarproject.bramble.mailbox.MailboxApi.TolerableFailureException;

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
public abstract class SimpleApiCall implements Supplier<Boolean> {

	private static final Logger LOG = getLogger(SimpleApiCall.class.getName());

	abstract void callApi()
			throws IOException, ApiException, TolerableFailureException;

	@Override
	public Boolean get() {
		try {
			callApi();
			return false; // Succeeded, don't retry
		} catch (IOException | ApiException e) {
			logException(LOG, WARNING, e);
			return true; // Failed, retry with backoff
		} catch (TolerableFailureException e) {
			logException(LOG, INFO, e);
			return false; // Failed tolerably, don't retry
		}
	}
}
