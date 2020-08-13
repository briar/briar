package org.briarproject.bramble.api.io;

import java.io.InputStream;

public interface TimeoutMonitor {

	/**
	 * Returns an {@link InputStream} that wraps the given stream and allows
	 * read timeouts to be detected.
	 * <p>
	 * The returned stream must be {@link InputStream#close() closed} when it's
	 * no longer needed to ensure that resources held by the timeout monitor
	 * are released.
	 *
	 * @param timeoutMs The read timeout in milliseconds. Timeouts will be
	 * detected eventually but are not guaranteed to be detected immediately.
	 */
	InputStream createTimeoutInputStream(InputStream in, long timeoutMs);
}
