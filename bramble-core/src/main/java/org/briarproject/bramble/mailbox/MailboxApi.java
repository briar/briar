package org.briarproject.bramble.mailbox;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.mailbox.MailboxAuthToken;
import org.briarproject.bramble.api.mailbox.MailboxFileId;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

interface MailboxApi {

	/**
	 * Sets up the mailbox with the setup token.
	 *
	 * @param properties MailboxProperties with the setup token
	 * @return the owner token
	 * @throws ApiException for 401 response.
	 */
	MailboxAuthToken setup(MailboxProperties properties)
			throws IOException, ApiException;

	/**
	 * Checks the status of the mailbox.
	 *
	 * @return true if the status is OK, false otherwise.
	 * @throws ApiException for 401 response.
	 */
	boolean checkStatus(MailboxProperties properties)
			throws IOException, ApiException;

	/**
	 * Unpairs Briar and the mailbox (owner only).
	 * Resets mailbox state to that after first install
	 * (e.g. removes all stored files as well).
	 */
	void wipeMailbox(MailboxProperties properties)
			throws IOException, ApiException;

	/**
	 * Adds a new contact to the mailbox.
	 *
	 * @throws TolerableFailureException if response code is 409
	 * (contact was already added).
	 */
	void addContact(MailboxProperties properties, MailboxContact contact)
			throws IOException, ApiException, TolerableFailureException;

	/**
	 * Deletes a contact from the mailbox.
	 * This should get called after a contact was removed from Briar.
	 *
	 * @throws TolerableFailureException if response code is 404
	 * (contact probably was already deleted).
	 */
	void deleteContact(MailboxProperties properties, ContactId contactId)
			throws IOException, ApiException, TolerableFailureException;

	/**
	 * Gets a list of {@link ContactId}s from the mailbox.
	 * These are the contacts that the mailbox already knows about.
	 */
	Collection<ContactId> getContacts(MailboxProperties properties)
			throws IOException, ApiException;

	/**
	 * Used by contacts to send files to the owner
	 * and by the owner to send files to contacts.
	 * <p>
	 * The owner can add files to the contacts' inboxes
	 * and the contacts can add files to their own outbox.
	 */
	void addFile(MailboxProperties properties, MailboxFolderId folderId,
			File file) throws IOException, ApiException;

	/**
	 * Used by owner and contacts to list their files to retrieve.
	 * <p>
	 * Returns 200 OK with the list of files in JSON.
	 */
	List<MailboxFile> getFiles(MailboxProperties properties,
			MailboxFolderId folderId) throws IOException, ApiException;

	/**
	 * Used by owner and contacts to retrieve a file.
	 * <p>
	 * Returns 200 OK if successful with the files' raw bytes
	 * in the response body.
	 *
	 * @param file the empty file the response bytes will be written into.
	 */
	void getFile(MailboxProperties properties, MailboxFolderId folderId,
			MailboxFileId fileId, File file) throws IOException, ApiException;

	/**
	 * Used by owner and contacts to delete files.
	 * <p>
	 * Returns 200 OK (no exception) if deletion was successful.
	 *
	 * @throws TolerableFailureException on 404 response,
	 * because file was most likely deleted already.
	 */
	void deleteFile(MailboxProperties properties, MailboxFolderId folderId,
			MailboxFileId fileId)
			throws IOException, ApiException, TolerableFailureException;

	/**
	 * Lists all contact outboxes that have files available
	 * for the owner to download.
	 *
	 * @return a list of folder names
	 * to be used with {@link #getFiles(MailboxProperties, MailboxFolderId)}.
	 * @throws IllegalArgumentException if used by non-owner.
	 */
	List<MailboxFolderId> getFolders(MailboxProperties properties)
			throws IOException, ApiException;

	@Immutable
	@JsonSerialize
	class MailboxContact {
		public final int contactId;
		public final MailboxAuthToken token;
		public final MailboxFolderId inboxId, outboxId;

		MailboxContact(ContactId contactId,
				MailboxAuthToken token,
				MailboxFolderId inboxId,
				MailboxFolderId outboxId) {
			this.contactId = contactId.getInt();
			this.token = token;
			this.inboxId = inboxId;
			this.outboxId = outboxId;
		}
	}

	@JsonSerialize
	class MailboxFile implements Comparable<MailboxFile> {
		public final MailboxFileId name;
		public final long time;

		public MailboxFile(MailboxFileId name, long time) {
			this.name = name;
			this.time = time;
		}

		@Override
		public int compareTo(@Nonnull MailboxApi.MailboxFile mailboxFile) {
			//noinspection UseCompareMethod
			return time < mailboxFile.time ? -1 :
					(time == mailboxFile.time ? 0 : 1);
		}
	}

	@Immutable
	class ApiException extends Exception {
	}

	@Immutable
	class MailboxAlreadyPairedException extends ApiException {
	}

	/**
	 * A failure that does not need to be retried,
	 * e.g. when adding a contact that already exists.
	 */
	@Immutable
	class TolerableFailureException extends Exception {
	}
}
