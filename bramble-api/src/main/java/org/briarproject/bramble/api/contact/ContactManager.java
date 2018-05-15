package org.briarproject.bramble.api.contact;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Collection;

@NotNullByDefault
public interface ContactManager {

	/**
	 * Registers a hook to be called whenever a contact is added or removed.
	 * This method should be called before
	 * {@link LifecycleManager#startServices(String)}.
	 */
	void registerContactHook(ContactHook hook);

	/**
	 * Stores a contact associated with the given local and remote pseudonyms,
	 * derives and stores transport keys for each transport, and returns an ID
	 * for the contact.
	 *
	 * @param alice true if the local party is Alice
	 */
	ContactId addContact(Transaction txn, Author remote, AuthorId local,
			SecretKey master, long timestamp, boolean alice, boolean verified,
			boolean active) throws DbException;

	/**
	 * Stores a contact associated with the given local and remote pseudonyms
	 * and returns an ID for the contact.
	 */
	ContactId addContact(Transaction txn, Author remote, AuthorId local,
			boolean verified, boolean active) throws DbException;

	/**
	 * Stores a contact associated with the given local and remote pseudonyms,
	 * derives and stores transport keys for each transport, and returns an ID
	 * for the contact.
	 *
	 * @param alice true if the local party is Alice
	 */
	ContactId addContact(Author remote, AuthorId local, SecretKey master,
			long timestamp, boolean alice, boolean verified, boolean active)
			throws DbException;

	/**
	 * Returns the contact with the given ID.
	 */
	Contact getContact(ContactId c) throws DbException;

	/**
	 * Returns the contact with the given remoteAuthorId
	 * that was added by the LocalAuthor with the given localAuthorId
	 *
	 * @throws org.briarproject.bramble.api.db.NoSuchContactException
	 */
	Contact getContact(AuthorId remoteAuthorId, AuthorId localAuthorId)
			throws DbException;

	/**
	 * Returns the contact with the given remoteAuthorId
	 * that was added by the LocalAuthor with the given localAuthorId
	 *
	 * @throws org.briarproject.bramble.api.db.NoSuchContactException
	 */
	Contact getContact(Transaction txn, AuthorId remoteAuthorId,
			AuthorId localAuthorId) throws DbException;

	/**
	 * Returns all active contacts.
	 */
	Collection<Contact> getActiveContacts() throws DbException;

	/**
	 * Removes a contact and all associated state.
	 */
	void removeContact(ContactId c) throws DbException;

	/**
	 * Removes a contact and all associated state.
	 */
	void removeContact(Transaction txn, ContactId c) throws DbException;

	/**
	 * Marks a contact as active or inactive.
	 */
	void setContactActive(Transaction txn, ContactId c, boolean active)
			throws DbException;

	/**
	 * Return true if a contact with this name and public key already exists
	 */
	boolean contactExists(Transaction txn, AuthorId remoteAuthorId,
			AuthorId localAuthorId) throws DbException;

	/**
	 * Return true if a contact with this name and public key already exists
	 */
	boolean contactExists(AuthorId remoteAuthorId, AuthorId localAuthorId)
			throws DbException;

	interface ContactHook {

		void addingContact(Transaction txn, Contact c) throws DbException;

		void removingContact(Transaction txn, Contact c) throws DbException;
	}
}
