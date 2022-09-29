package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.nullsafety.NotNullByDefault;

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
	 *
	 * @param properties Properties for communicating with the mailbox
	 * managed by this client.
	 * @param folderId The ID of the folder to which files will be uploaded.
	 */
	void assignContactForUpload(ContactId c, MailboxProperties properties,
			MailboxFolderId folderId);

	/**
	 * Deassigns a contact from the client for upload.
	 */
	void deassignContactForUpload(ContactId c);

	/**
	 * Assigns a contact to the client for download.
	 *
	 * @param properties Properties for communicating with the mailbox
	 * managed by this client.
	 * @param folderId The ID of the folder from which files will be
	 * downloaded.
	 */
	void assignContactForDownload(ContactId c, MailboxProperties properties,
			MailboxFolderId folderId);

	/**
	 * Deassigns a contact from the client for download.
	 */
	void deassignContactForDownload(ContactId c);
}
