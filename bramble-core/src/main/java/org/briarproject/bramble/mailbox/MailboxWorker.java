package org.briarproject.bramble.mailbox;

import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A worker that downloads files from a contact's mailbox.
 */
@ThreadSafe
@NotNullByDefault
interface MailboxWorker {

	/**
	 * Asynchronously starts the worker.
	 */
	void start();

	/**
	 * Destroys the worker and cancels any pending tasks or retries.
	 */
	void destroy();
}
