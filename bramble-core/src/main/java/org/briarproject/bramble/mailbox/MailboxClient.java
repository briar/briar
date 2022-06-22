package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
interface MailboxClient {

	/**
	 * Asynchronously starts the client.
	 */
	void start();

	/**
	 * Destroys the client and its workers, cancelling any pending tasks or
	 * retries.
	 */
	void destroy();

	/**
	 * Assigns a contact to the client for upload.
	 */
	void assignContactForUpload(ContactId c, MailboxProperties properties,
			MailboxFolderId folderId);

	/**
	 * Deassigns a contact from the client for upload.
	 */
	void deassignContactForUpload(ContactId c);

	/**
	 * Assigns a contact to the client for download.
	 */
	void assignContactForDownload(ContactId c, MailboxProperties properties,
			MailboxFolderId folderId);

	/**
	 * Deassigns a contact from the client for download.
	 */
	void deassignContactForDownload(ContactId c);
}
