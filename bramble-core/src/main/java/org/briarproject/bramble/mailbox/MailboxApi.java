package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.mailbox.MailboxProperties;

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
	class PermanentFailureException extends Exception {
	}
}
