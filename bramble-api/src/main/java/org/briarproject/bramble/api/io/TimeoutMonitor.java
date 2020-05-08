package org.briarproject.bramble.api.io;

import java.io.InputStream;

public interface TimeoutMonitor {

	/**
	 * Returns an {@link InputStream} that wraps the given stream and allows
	 * read timeouts to be detected.
	 *
	 * @param timeoutMs The read timeout in milliseconds. Timeouts will be
	 * detected eventually but are not guaranteed to be detected immediately.
	 */
	InputStream createTimeoutInputStream(InputStream in, long timeoutMs);
}
