package org.briarproject.bramble.mailbox;

import java.io.IOException;

import javax.annotation.concurrent.Immutable;

interface MailboxApi {

	/**
	 * Sets up the mailbox with the setup token.
	 *
	 * @param properties MailboxProperties with the setup token
	 * @return the owner token
	 * @throws PermanentFailureException for 401 response.
	 */
	String setup(MailboxProperties properties)
			throws IOException, PermanentFailureException;

	/**
	 * Checks the status of the mailbox.
	 *
	 * @return true if the status is OK, false otherwise.
	 * @throws PermanentFailureException for 401 response.
	 */
	boolean checkStatus(MailboxProperties properties)
			throws IOException, PermanentFailureException;

	@Immutable
	class MailboxProperties {
		final String baseUrl;
		final String token;
		final boolean isOwner;

		MailboxProperties(String baseUrl, String token, boolean isOwner) {
			this.baseUrl = baseUrl;
			this.token = token;
			this.isOwner = isOwner;
		}
	}

	@Immutable
	class PermanentFailureException extends Exception {
		/**
		 * If true, the failure is fatal and requires user attention.
		 * The entire task queue will most likely need to stop.
		 */
		final boolean fatal;

		PermanentFailureException(boolean fatal) {
			this.fatal = fatal;
		}
	}
}
