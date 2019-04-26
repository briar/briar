package org.briarproject.bramble.api.contact;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.UnsupportedVersionException;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.AuthorInfo;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Collection;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

@NotNullByDefault
public interface ContactManager {

	int LINK_LENGTH = 64;
	Pattern LINK_REGEX =
			Pattern.compile("(briar://)?([a-z2-7]{" + LINK_LENGTH + "})");

	/**
	 * Registers a hook to be called whenever a contact is added or removed.
	 * This method should be called before
	 * {@link LifecycleManager#startServices(SecretKey)}.
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
			SecretKey rootKey, long timestamp, boolean alice, boolean verified,
			boolean active) throws DbException;

	/**
	 * Stores a contact associated with the given local and remote pseudonyms
	 * and returns an ID for the contact.
	 */
	ContactId addContact(Transaction txn, Author remote, AuthorId local,
			boolean verified) throws DbException;

	/**
	 * Stores a contact associated with the given local and remote pseudonyms,
	 * derives and stores transport keys for each transport, and returns an ID
	 * for the contact.
	 *
	 * @param alice true if the local party is Alice
	 */
	ContactId addContact(Author remote, AuthorId local, SecretKey rootKey,
			long timestamp, boolean alice, boolean verified, boolean active)
			throws DbException;

	/**
	 * Returns the handshake link that needs to be sent to a contact we want
	 * to add.
	 */
	String getHandshakeLink() throws DbException;

	/**
	 * Creates a {@link PendingContact} from the given handshake link and
	 * alias, adds it to the database and returns it.
	 *
	 * @param link The handshake link received from the contact we want to add
	 * @param alias The alias the user has given this contact
	 * @return A PendingContact representing the contact to be added
	 * @throws UnsupportedVersionException If the link uses a format version
	 * that is not supported
	 * @throws FormatException If the link is invalid
	 */
	PendingContact addPendingContact(String link, String alias)
			throws DbException, FormatException;

	/**
	 * Returns a list of {@link PendingContact}s.
	 */
	Collection<PendingContact> getPendingContacts() throws DbException;

	/**
	 * Removes a {@link PendingContact}.
	 */
	void removePendingContact(PendingContactId p) throws DbException;

	/**
	 * Returns the contact with the given ID.
	 */
	Contact getContact(ContactId c) throws DbException;

	/**
	 * Returns the contact with the given remoteAuthorId
	 * that was added by the LocalAuthor with the given localAuthorId
	 *
	 * @throws NoSuchContactException If the contact is not in the database
	 */
	Contact getContact(AuthorId remoteAuthorId, AuthorId localAuthorId)
			throws DbException;

	/**
	 * Returns the contact with the given remoteAuthorId
	 * that was added by the LocalAuthor with the given localAuthorId
	 *
	 * @throws NoSuchContactException If the contact is not in the database
	 */
	Contact getContact(Transaction txn, AuthorId remoteAuthorId,
			AuthorId localAuthorId) throws DbException;

	/**
	 * Returns all active contacts.
	 */
	Collection<Contact> getContacts() throws DbException;

	/**
	 * Removes a contact and all associated state.
	 */
	void removeContact(ContactId c) throws DbException;

	/**
	 * Removes a contact and all associated state.
	 */
	void removeContact(Transaction txn, ContactId c) throws DbException;

	/**
	 * Sets an alias name for the contact or unsets it if alias is null.
	 */
	void setContactAlias(Transaction txn, ContactId c, @Nullable String alias)
			throws DbException;

	/**
	 * Sets an alias name for the contact or unsets it if alias is null.
	 */
	void setContactAlias(ContactId c, @Nullable String alias)
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

	/**
	 * Returns the {@link AuthorInfo} for the given author.
	 */
	AuthorInfo getAuthorInfo(AuthorId a) throws DbException;

	/**
	 * Returns the {@link AuthorInfo} for the given author.
	 */
	AuthorInfo getAuthorInfo(Transaction txn, AuthorId a) throws DbException;

	interface ContactHook {

		/**
		 * Called when a contact is being added.
		 *
		 * @param txn A read-write transaction
		 * @param c The contact that is being added
		 */
		void addingContact(Transaction txn, Contact c) throws DbException;

		/**
		 * Called when a contact is being removed
		 *
		 * @param txn A read-write transaction
		 * @param c The contact that is being removed
		 */
		void removingContact(Transaction txn, Contact c) throws DbException;
	}
}
