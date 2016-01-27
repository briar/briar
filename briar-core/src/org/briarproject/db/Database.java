package org.briarproject.db;

import org.briarproject.api.DeviceId;
import org.briarproject.api.TransportId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.StorageStatus;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.settings.Settings;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageStatus;
import org.briarproject.api.sync.ValidationManager.Validity;
import org.briarproject.api.transport.TransportKeys;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * A low-level interface to the database (DatabaseComponent provides a
 * high-level interface). Most operations take a transaction argument, which is
 * obtained by calling {@link #startTransaction()}. Every transaction must be
 * terminated by calling either {@link #abortTransaction(T)} or
 * {@link #commitTransaction(T)}, even if an exception is thrown.
 * <p>
 * Read-write locking is provided by the DatabaseComponent implementation.
 */
interface Database<T> {

	/**
	 * Opens the database and returns true if the database already existed.
	 * <p>
	 * Locking: write.
	 */
	boolean open() throws DbException;

	/**
	 * Prevents new transactions from starting, waits for all current
	 * transactions to finish, and closes the database.
	 * <p>
	 * Locking: write.
	 */
	void close() throws DbException, IOException;

	/** Starts a new transaction and returns an object representing it. */
	T startTransaction() throws DbException;

	/**
	 * Aborts the given transaction - no changes made during the transaction
	 * will be applied to the database.
	 */
	void abortTransaction(T txn);

	/**
	 * Commits the given transaction - all changes made during the transaction
	 * will be applied to the database.
	 */
	void commitTransaction(T txn) throws DbException;

	/**
	 * Returns the number of transactions started since the transaction count
	 * was last reset.
	 */
	int getTransactionCount();

	/**  Resets the transaction count. */
	void resetTransactionCount();

	/**
	 * Stores a contact associated with the given local and remote pseudonyms,
	 * and returns an ID for the contact.
	 * <p>
	 * Locking: write.
	 */
	ContactId addContact(T txn, Author remote, AuthorId local)
			throws DbException;

	/**
	 * Adds a group to the given contact's subscriptions.
	 * <p>
	 * Locking: write.
	 */
	void addContactGroup(T txn, ContactId c, Group g) throws DbException;

	/**
	 * Subscribes to a group, or returns false if the user already has the
	 * maximum number of subscriptions.
	 * <p>
	 * Locking: write.
	 */
	boolean addGroup(T txn, Group g) throws DbException;

	/**
	 * Stores a local pseudonym.
	 * <p>
	 * Locking: write.
	 */
	void addLocalAuthor(T txn, LocalAuthor a) throws DbException;

	/**
	 * Stores a message.
	 * <p>
	 * Locking: write.
	 */
	void addMessage(T txn, Message m, Validity validity, boolean shared)
			throws DbException;

	/**
	 * Records that a message has been offered by the given contact.
	 * <p>
	 * Locking: write.
	 */
	void addOfferedMessage(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Initialises the status of the given message with respect to the given
	 * contact.
	 * <p>
	 * Locking: write.
	 * @param ack whether the message needs to be acknowledged.
	 * @param seen whether the contact has seen the message.
	 */
	void addStatus(T txn, ContactId c, MessageId m, boolean ack, boolean seen)
			throws DbException;

	/**
	 * Stores a transport and returns true if the transport was not previously
	 * in the database.
	 * <p>
	 * Locking: write.
	 */
	boolean addTransport(T txn, TransportId t, int maxLatency)
			throws DbException;

	/**
	 * Stores the given transport keys for a newly added contact.
	 * <p>
	 * Locking: write.
	 */
	void addTransportKeys(T txn, ContactId c, TransportKeys k) throws DbException;

	/**
	 * Makes a group visible to the given contact.
	 * <p>
	 * Locking: write.
	 */
	void addVisibility(T txn, ContactId c, GroupId g) throws DbException;

	/**
	 * Returns true if the database contains the given contact for the given
	 * local pseudonym.
	 * <p>
	 * Locking: read.
	 */
	boolean containsContact(T txn, AuthorId remote, AuthorId local)
			throws DbException;

	/**
	 * Returns true if the database contains the given contact.
	 * <p>
	 * Locking: read.
	 */
	boolean containsContact(T txn, ContactId c) throws DbException;

	/**
	 * Returns true if the user subscribes to the given group.
	 * <p>
	 * Locking: read.
	 */
	boolean containsGroup(T txn, GroupId g) throws DbException;

	/**
	 * Returns true if the database contains the given local pseudonym.
	 * <p>
	 * Locking: read.
	 */
	boolean containsLocalAuthor(T txn, AuthorId a) throws DbException;

	/**
	 * Returns true if the database contains the given message.
	 * <p>
	 * Locking: read.
	 */
	boolean containsMessage(T txn, MessageId m) throws DbException;

	/**
	 * Returns true if the database contains the given transport.
	 * <p>
	 * Locking: read.
	 */
	boolean containsTransport(T txn, TransportId t) throws DbException;

	/**
	 * Returns true if the user subscribes to the given group and the group is
	 * visible to the given contact.
	 * <p>
	 * Locking: read.
	 */
	boolean containsVisibleGroup(T txn, ContactId c, GroupId g)
			throws DbException;

	/**
	 * Returns true if the database contains the given message and the message
	 * is visible to the given contact.
	 * <p>
	 * Locking: read.
	 */
	boolean containsVisibleMessage(T txn, ContactId c, MessageId m)
			throws DbException;

	/**
	 * Returns the number of messages offered by the given contact.
	 * <p>
	 * Locking: read.
	 */
	int countOfferedMessages(T txn, ContactId c) throws DbException;

	/**
	 * Returns all groups belonging to the given client to which the user could
	 * subscribe.
	 * <p>
	 * Locking: read.
	 */
	Collection<Group> getAvailableGroups(T txn, ClientId c) throws DbException;

	/**
	 * Returns the contact with the given ID.
	 * <p>
	 * Locking: read.
	 */
	Contact getContact(T txn, ContactId c) throws DbException;

	/**
	 * Returns the IDs of all contacts.
	 * <p>
	 * Locking: read.
	 */
	Collection<ContactId> getContactIds(T txn) throws DbException;

	/**
	 * Returns all contacts.
	 * <p>
	 * Locking: read.
	 */
	Collection<Contact> getContacts(T txn) throws DbException;

	/**
	 * Returns all contacts associated with the given local pseudonym.
	 * <p>
	 * Locking: read.
	 */
	Collection<ContactId> getContacts(T txn, AuthorId a) throws DbException;

	/**
	 * Returns the unique ID for this device.
	 * <p>
	 * Locking: read.
	 */
	DeviceId getDeviceId(T txn) throws DbException;

	/**
	 * Returns the amount of free storage space available to the database, in
	 * bytes. This is based on the minimum of the space available on the device
	 * where the database is stored and the database's configured size.
	 */
	long getFreeSpace() throws DbException;

	/**
	 * Returns the group with the given ID, if the user subscribes to it.
	 * <p>
	 * Locking: read.
	 */
	Group getGroup(T txn, GroupId g) throws DbException;

	/**
	 * Returns the metadata for the given group.
	 * <p>
	 * Locking: read.
	 */
	Metadata getGroupMetadata(T txn, GroupId g) throws DbException;

	/**
	 * Returns all groups belonging to the given client to which the user
	 * subscribes.
	 * <p>
	 * Locking: read.
	 */
	Collection<Group> getGroups(T txn, ClientId c) throws DbException;

	/**
	 * Returns the local pseudonym with the given ID.
	 * <p>
	 * Locking: read.
	 */
	LocalAuthor getLocalAuthor(T txn, AuthorId a) throws DbException;

	/**
	 * Returns all local pseudonyms.
	 * <p>
	 * Locking: read.
	 */
	Collection<LocalAuthor> getLocalAuthors(T txn) throws DbException;

	/**
	 * Returns the metadata for all messages in the given group.
	 * <p>
	 * Locking: read.
	 */
	Map<MessageId, Metadata> getMessageMetadata(T txn, GroupId g)
			throws DbException;

	/**
	 * Returns the metadata for the given message.
	 * <p>
	 * Locking: read.
	 */
	Metadata getMessageMetadata(T txn, MessageId m) throws DbException;

	/**
	 * Returns the status of all messages in the given group with respect
	 * to the given contact.
	 * <p>
	 * Locking: read
	 */
	Collection<MessageStatus> getMessageStatus(T txn, ContactId c, GroupId g)
			throws DbException;

	/**
	 * Returns the status of the given message with respect to the given
	 * contact.
	 * <p>
	 * Locking: read
	 */
	MessageStatus getMessageStatus(T txn, ContactId c, MessageId m)
			throws DbException;

	/**
	 * Returns the IDs of some messages received from the given contact that
	 * need to be acknowledged, up to the given number of messages.
	 * <p>
	 * Locking: read.
	 */
	Collection<MessageId> getMessagesToAck(T txn, ContactId c, int maxMessages)
			throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be offered to the
	 * given contact, up to the given number of messages.
	 * <p>
	 * Locking: read.
	 */
	Collection<MessageId> getMessagesToOffer(T txn, ContactId c,
			int maxMessages) throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be sent to the
	 * given contact, up to the given total length.
	 * <p>
	 * Locking: read.
	 */
	Collection<MessageId> getMessagesToSend(T txn, ContactId c, int maxLength)
			throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be requested from
	 * the given contact, up to the given number of messages.
	 * <p>
	 * Locking: read.
	 */
	Collection<MessageId> getMessagesToRequest(T txn, ContactId c,
			int maxMessages) throws DbException;

	/**
	 * Returns the IDs of any messages that need to be validated by the given
	 * client.
	 * <p>
	 * Locking: read.
	 */
	Collection<MessageId> getMessagesToValidate(T txn, ClientId c)
			throws DbException;

	/**
	 * Returns the message with the given ID, in serialised form.
	 * <p>
	 * Locking: read.
	 */
	byte[] getRawMessage(T txn, MessageId m) throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be sent to the
	 * given contact and have been requested by the contact, up to the given
	 * total length.
	 * <p>
	 * Locking: read.
	 */
	Collection<MessageId> getRequestedMessagesToSend(T txn, ContactId c,
			int maxLength) throws DbException;

	/**
	 * Returns all settings in the given namespace.
	 * <p>
	 * Locking: read.
	 */
	Settings getSettings(T txn, String namespace) throws DbException;

	/**
	 * Returns all contacts who subscribe to the given group.
	 * <p>
	 * Locking: read.
	 */
	Collection<Contact> getSubscribers(T txn, GroupId g) throws DbException;

	/**
	 * Returns all transport keys for the given transport.
	 * <p>
	 * Locking: read.
	 */
	Map<ContactId, TransportKeys> getTransportKeys(T txn, TransportId t)
			throws DbException;

	/**
	 * Returns the maximum latencies in milliseconds of all transports.
	 * <p>
	 * Locking: read.
	 */
	Map<TransportId, Integer> getTransportLatencies(T txn) throws DbException;

	/**
	 * Returns the IDs of all contacts to which the given group is visible.
	 * <p>
	 * Locking: read.
	 */
	Collection<ContactId> getVisibility(T txn, GroupId g) throws DbException;

	/**
	 * Increments the outgoing stream counter for the given contact and
	 * transport in the given rotation period.
	 * <p>
	 * Locking: write.
	 */
	void incrementStreamCounter(T txn, ContactId c, TransportId t,
			long rotationPeriod) throws DbException;

	/**
	 * Marks the given messages as not needing to be acknowledged to the
	 * given contact.
	 * <p>
	 * Locking: write.
	 */
	void lowerAckFlag(T txn, ContactId c, Collection<MessageId> acked)
			throws DbException;

	/**
	 * Marks the given messages as not having been requested by the given
	 * contact.
	 * <p>
	 * Locking: write.
	 */
	void lowerRequestedFlag(T txn, ContactId c, Collection<MessageId> requested)
			throws DbException;

	/*
	 * Merges the given metadata with the existing metadata for the given
	 * group.
	 * <p>
	 * Locking: write.
	 */
	void mergeGroupMetadata(T txn, GroupId g, Metadata meta)
			throws DbException;

	/*
	 * Merges the given metadata with the existing metadata for the given
	 * message.
	 * <p>
	 * Locking: write.
	 */
	void mergeMessageMetadata(T txn, MessageId m, Metadata meta)
			throws DbException;

	/**
	 * Merges the given settings with the existing settings in the given
	 * namespace.
	 * <p>
	 * Locking: write.
	 */
	void mergeSettings(T txn, Settings s, String namespace) throws DbException;

	/**
	 * Marks a message as needing to be acknowledged to the given contact.
	 * <p>
	 * Locking: write.
	 */
	void raiseAckFlag(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Marks a message as having been requested by the given contact.
	 * <p>
	 * Locking: write.
	 */
	void raiseRequestedFlag(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Marks a message as having been seen by the given contact.
	 * <p>
	 * Locking: write.
	 */
	void raiseSeenFlag(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Removes a contact from the database.
	 * <p>
	 * Locking: write.
	 */
	void removeContact(T txn, ContactId c) throws DbException;

	/**
	 * Unsubscribes from a group. Any messages belonging to the group are
	 * deleted from the database.
	 * <p>
	 * Locking: write.
	 */
	void removeGroup(T txn, GroupId g) throws DbException;

	/**
	 * Removes a local pseudonym (and all associated contacts) from the
	 * database.
	 * <p>
	 * Locking: write.
	 */
	void removeLocalAuthor(T txn, AuthorId a) throws DbException;

	/**
	 * Removes a message (and all associated state) from the database.
	 * <p>
	 * Locking: write.
	 */
	void removeMessage(T txn, MessageId m) throws DbException;

	/**
	 * Removes an offered message that was offered by the given contact, or
	 * returns false if there is no such message.
	 * <p>
	 * Locking: write.
	 */
	boolean removeOfferedMessage(T txn, ContactId c, MessageId m)
			throws DbException;

	/**
	 * Removes the given offered messages that were offered by the given
	 * contact.
	 * <p>
	 * Locking: write.
	 */
	void removeOfferedMessages(T txn, ContactId c,
			Collection<MessageId> requested) throws DbException;

	/**
	 * Removes a transport (and all associated state) from the database.
	 * <p>
	 * Locking: write.
	 */
	void removeTransport(T txn, TransportId t) throws DbException;

	/**
	 * Makes a group invisible to the given contact.
	 * <p>
	 * Locking: write.
	 */
	void removeVisibility(T txn, ContactId c, GroupId g) throws DbException;

	/**
	 * Resets the transmission count and expiry time of the given message with
	 * respect to the given contact.
	 * <p>
	 * Locking: write.
	 */
	void resetExpiryTime(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Sets the status of the given contact.
	 * <p>
	 * Locking: write.
	 */
	void setContactStatus(T txn, ContactId c, StorageStatus s)
			throws DbException;

	/**
	 * Sets the status of the given local pseudonym.
	 * <p>
	 * Locking: write.
	 */
	void setLocalAuthorStatus(T txn, AuthorId a, StorageStatus s)
			throws DbException;

	/**
	 * Marks the given message as shared or unshared.
	 * <p>
	 * Locking: write.
	 */
	void setMessageShared(T txn, MessageId m, boolean shared)
			throws DbException;

	/**
	 * Marks the given message as valid or invalid.
	 * <p>
	 * Locking: write.
	 */
	void setMessageValid(T txn, MessageId m, boolean valid) throws DbException;

	/**
	 * Sets the reordering window for the given contact and transport in the
	 * given rotation period.
	 * <p>
	 * Locking: write.
	 */
	void setReorderingWindow(T txn, ContactId c, TransportId t,
			long rotationPeriod, long base, byte[] bitmap) throws DbException;

	/**
	 * Updates the given contact's subscriptions and returns true, unless an
	 * update with an equal or higher version number has already been received
	 * from the contact.
	 * <p>
	 * Locking: write.
	 */
	boolean setGroups(T txn, ContactId c, Collection<Group> groups,
			long version) throws DbException;

	/**
	 * Records a subscription ack from the given contact for the given version,
	 * unless the contact has already acked an equal or higher version.
	 * <p>
	 * Locking: write.
	 */
	void setSubscriptionUpdateAcked(T txn, ContactId c, long version)
			throws DbException;

	/**
	 * Makes a group visible or invisible to future contacts by default.
	 * <p>
	 * Locking: write.
	 */
	void setVisibleToAll(T txn, GroupId g, boolean all) throws DbException;

	/**
	 * Updates the transmission count and expiry time of the given message
	 * with respect to the given contact, using the latency of the transport
	 * over which it was sent.
	 * <p>
	 * Locking: write.
	 */
	void updateExpiryTime(T txn, ContactId c, MessageId m, int maxLatency)
			throws DbException;

	/**
	 * Stores the given transport keys, deleting any keys they have replaced.
	 * <p>
	 * Locking: write.
	 */
	void updateTransportKeys(T txn, Map<ContactId, TransportKeys> keys)
			throws DbException;
}
