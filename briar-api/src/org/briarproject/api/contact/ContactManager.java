package org.briarproject.api.contact;

import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;

import java.util.Collection;

public interface ContactManager {

	/** Registers a hook to be called whenever a contact is added. */
	void registerAddContactHook(AddContactHook hook);

	/** Registers a hook to be called whenever a contact is removed. */
	void registerRemoveContactHook(RemoveContactHook hook);

	/**
	 * Stores a contact within the given transaction associated with the given
	 * local and remote pseudonyms, and returns an ID for the contact.
	 */
	ContactId addContact(Transaction txn, Author remote, AuthorId local,
			SecretKey master, long timestamp, boolean alice, boolean verified,
			boolean active) throws DbException;

	/**
	 * Stores a contact associated with the given local and remote pseudonyms,
	 * and returns an ID for the contact.
	 */
	ContactId addContact(Author remote, AuthorId local,
			SecretKey master, long timestamp, boolean alice, boolean verified,
			boolean active) throws DbException;

	/** Returns the contact with the given ID. */
	Contact getContact(ContactId c) throws DbException;

	/** Returns all active contacts. */
	Collection<Contact> getActiveContacts() throws DbException;

	/** Removes a contact and all associated state. */
	void removeContact(ContactId c) throws DbException;

	/** Removes a contact and all associated state. */
	void removeContact(Transaction txn, ContactId c) throws DbException;

	/** Marks a contact as active or inactive. */
	void setContactActive(Transaction txn, ContactId c, boolean active)
			throws DbException;

	/** Return true if a contact with this name and public key already exists */
	boolean contactExists(Transaction txn, AuthorId remoteAuthorId,
			AuthorId localAuthorId) throws DbException;

	/** Return true if a contact with this name and public key already exists */
	boolean contactExists(AuthorId remoteAuthorId, AuthorId localAuthorId)
			throws DbException;

	interface AddContactHook {
		void addingContact(Transaction txn, Contact c) throws DbException;
	}

	interface RemoveContactHook {
		void removingContact(Transaction txn, Contact c) throws DbException;
	}
}
