package org.briarproject.bramble.api.contact;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.UnsupportedVersionException;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.ContactExistsException;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.db.PendingContactExistsException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.AuthorInfo;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.security.GeneralSecurityException;
import java.util.Collection;

import javax.annotation.Nullable;

@NotNullByDefault
public interface ContactManager {

	/**
	 * Registers a hook to be called whenever a contact is added or removed.
	 * This method should be called before
	 * {@link LifecycleManager#startServices(SecretKey)}.
	 */
	void registerContactHook(ContactHook hook);

	/**
	 * Stores a contact associated with the given local and remote pseudonyms,
	 * derives and stores rotation mode transport keys for each transport, and
	 * returns an ID for the contact.
	 *
	 * @param rootKey The root key for a set of rotation mode transport keys
	 * @param timestamp The timestamp for deriving rotation mode transport
	 * keys from the root key
	 * @param alice True if the local party is Alice
	 * @param verified True if the contact's identity has been verified, which
	 * is true if the contact was added in person or false if the contact was
	 * introduced or added remotely
	 * @param active True if the rotation mode transport keys can be used for
	 * outgoing streams
	 */
	ContactId addContact(Transaction txn, Author remote, AuthorId local,
			SecretKey rootKey, long timestamp, boolean alice, boolean verified,
			boolean active) throws DbException;

	/**
	 * Stores a contact associated with the given local and remote pseudonyms,
	 * replacing the given pending contact, derives and stores handshake mode
	 * and rotation mode transport keys for each transport, and returns an ID
	 * for the contact.
	 *
	 * @param rootKey The root key for a set of rotation mode transport keys
	 * @param timestamp The timestamp for deriving rotation mode transport
	 * keys from the root key
	 * @param alice True if the local party is Alice
	 * @param verified True if the contact's identity has been verified, which
	 * is true if the contact was added in person or false if the contact was
	 * introduced or added remotely
	 * @param active True if the rotation mode transport keys can be used for
	 * outgoing streams
	 * @throws GeneralSecurityException If the pending contact's handshake
	 * public key is invalid
	 */
	ContactId addContact(Transaction txn, PendingContactId p, Author remote,
			AuthorId local, SecretKey rootKey, long timestamp, boolean alice,
			boolean verified, boolean active)
			throws DbException, GeneralSecurityException;

	/**
	 * Stores a contact associated with the given local and remote pseudonyms
	 * and returns an ID for the contact.
	 *
	 * @param verified True if the contact's identity has been verified, which
	 * is true if the contact was added in person or false if the contact was
	 * introduced or added remotely
	 */
	ContactId addContact(Transaction txn, Author remote, AuthorId local,
			boolean verified) throws DbException;

	/**
	 * Stores a contact associated with the given local and remote pseudonyms,
	 * derives and stores rotation mode transport keys for each transport, and
	 * returns an ID for the contact.
	 *
	 * @param rootKey The root key for a set of rotation mode transport keys
	 * @param timestamp The timestamp for deriving rotation mode transport
	 * keys from the root key
	 * @param alice True if the local party is Alice
	 * @param verified True if the contact's identity has been verified, which
	 * is true if the contact was added in person or false if the contact was
	 * introduced or added remotely
	 * @param active True if the rotation mode transport keys can be used for
	 * outgoing streams
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
	 * @param link The handshake link received from the pending contact
	 * @param alias The alias the user has given this pending contact
	 * @throws UnsupportedVersionException If the link uses a format version
	 * that is not supported
	 * @throws FormatException If the link is invalid
	 * @throws GeneralSecurityException If the pending contact's handshake
	 * public key is invalid
	 * @throws ContactExistsException If a contact with the same handshake
	 * public key already exists
	 * @throws PendingContactExistsException If a pending contact with the same
	 * handshake public key already exists
	 */
	PendingContact addPendingContact(String link, String alias)
			throws DbException, FormatException, GeneralSecurityException,
			ContactExistsException, PendingContactExistsException;

	/**
	 * Returns the pending contact with the given ID.
	 */
	PendingContact getPendingContact(Transaction txn, PendingContactId p)
			throws DbException;

	/**
	 * Returns a list of {@link PendingContact PendingContacts} and their
	 * {@link PendingContactState states}.
	 */
	Collection<Pair<PendingContact, PendingContactState>> getPendingContacts()
			throws DbException;

	/**
	 * Removes a {@link PendingContact}.
	 */
	void removePendingContact(PendingContactId p) throws DbException;

	/**
	 * Returns the contact with the given ID.
	 */
	Contact getContact(ContactId c) throws DbException;

	/**
	 * Returns the contact with the given ID.
	 */
	Contact getContact(Transaction txn, ContactId c) throws DbException;

	/**
	 * Returns the contact with the given {@code remoteAuthorId}
	 * that belongs to the local pseudonym with the given {@code localAuthorId}.
	 *
	 * @throws NoSuchContactException If the contact is not in the database
	 */
	Contact getContact(AuthorId remoteAuthorId, AuthorId localAuthorId)
			throws DbException;

	/**
	 * Returns the contact with the given {@code remoteAuthorId}
	 * that belongs to the local pseudonym with the given {@code localAuthorId}.
	 *
	 * @throws NoSuchContactException If the contact is not in the database
	 */
	Contact getContact(Transaction txn, AuthorId remoteAuthorId,
			AuthorId localAuthorId) throws DbException;

	/**
	 * Returns all contacts.
	 */
	Collection<Contact> getContacts() throws DbException;

	/**
	 * Returns all contacts.
	 */
	Collection<Contact> getContacts(Transaction txn) throws DbException;

	/**
	 * Removes a contact and all associated state.
	 */
	void removeContact(ContactId c) throws DbException;

	/**
	 * Removes a contact and all associated state.
	 */
	void removeContact(Transaction txn, ContactId c) throws DbException;

	/**
	 * Sets an alias for the contact or unsets it if {@code alias} is null.
	 */
	void setContactAlias(Transaction txn, ContactId c, @Nullable String alias)
			throws DbException;

	/**
	 * Sets an alias for the contact or unsets it if {@code alias} is null.
	 */
	void setContactAlias(ContactId c, @Nullable String alias)
			throws DbException;

	/**
	 * Returns true if a contact with this {@code remoteAuthorId} belongs to
	 * the local pseudonym with this {@code localAuthorId}.
	 */
	boolean contactExists(Transaction txn, AuthorId remoteAuthorId,
			AuthorId localAuthorId) throws DbException;

	/**
	 * Returns true if a contact with this {@code remoteAuthorId} belongs to
	 * the local pseudonym with this {@code localAuthorId}.
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
