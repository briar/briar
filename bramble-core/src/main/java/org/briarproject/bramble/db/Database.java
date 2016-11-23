package org.briarproject.bramble.db;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Group.Visibility;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.MessageStatus;
import org.briarproject.bramble.api.sync.ValidationManager.State;
import org.briarproject.bramble.api.transport.TransportKeys;

import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A low-level interface to the database (DatabaseComponent provides a
 * high-level interface). Most operations take a transaction argument, which is
 * obtained by calling {@link #startTransaction()}. Every transaction must be
 * terminated by calling either {@link #abortTransaction(T)} or
 * {@link #commitTransaction(T)}, even if an exception is thrown.
 */
@NotNullByDefault
interface Database<T> {

	/**
	 * Opens the database and returns true if the database already existed.
	 */
	boolean open() throws DbException;

	/**
	 * Prevents new transactions from starting, waits for all current
	 * transactions to finish, and closes the database.
	 */
	void close() throws DbException;

	/**
	 * Starts a new transaction and returns an object representing it.
	 */
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
	 * Stores a contact associated with the given local and remote pseudonyms,
	 * and returns an ID for the contact.
	 */
	ContactId addContact(T txn, Author remote, AuthorId local, boolean verified,
			boolean active) throws DbException;

	/**
	 * Stores a group.
	 */
	void addGroup(T txn, Group g) throws DbException;

	/**
	 * Sets the given group's visibility to the given contact to either
	 * {@link Visibility VISIBLE} or {@link Visibility SHARED}.
	 */
	void addGroupVisibility(T txn, ContactId c, GroupId g, boolean shared)
			throws DbException;

	/**
	 * Stores a local pseudonym.
	 */
	void addLocalAuthor(T txn, LocalAuthor a) throws DbException;

	/**
	 * Stores a message.
	 */
	void addMessage(T txn, Message m, State state, boolean shared)
			throws DbException;

	/**
	 * Adds a dependency between two messages in the given group.
	 */
	void addMessageDependency(T txn, GroupId g, MessageId dependent,
			MessageId dependency) throws DbException;

	/**
	 * Records that a message has been offered by the given contact.
	 */
	void addOfferedMessage(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Initialises the status of the given message with respect to the given
	 * contact.
	 *
	 * @param ack whether the message needs to be acknowledged.
	 * @param seen whether the contact has seen the message.
	 */
	void addStatus(T txn, ContactId c, MessageId m, boolean ack, boolean seen)
			throws DbException;

	/**
	 * Stores a transport.
	 */
	void addTransport(T txn, TransportId t, int maxLatency)
			throws DbException;

	/**
	 * Stores transport keys for a newly added contact.
	 */
	void addTransportKeys(T txn, ContactId c, TransportKeys k)
			throws DbException;

	/**
	 * Returns true if the database contains the given contact for the given
	 * local pseudonym.
	 * <p/>
	 * Read-only.
	 */
	boolean containsContact(T txn, AuthorId remote, AuthorId local)
			throws DbException;

	/**
	 * Returns true if the database contains the given contact.
	 * <p/>
	 * Read-only.
	 */
	boolean containsContact(T txn, ContactId c) throws DbException;

	/**
	 * Returns true if the database contains the given group.
	 * <p/>
	 * Read-only.
	 */
	boolean containsGroup(T txn, GroupId g) throws DbException;

	/**
	 * Returns true if the database contains the given local pseudonym.
	 * <p/>
	 * Read-only.
	 */
	boolean containsLocalAuthor(T txn, AuthorId a) throws DbException;

	/**
	 * Returns true if the database contains the given message.
	 * <p/>
	 * Read-only.
	 */
	boolean containsMessage(T txn, MessageId m) throws DbException;

	/**
	 * Returns true if the database contains the given transport.
	 * <p/>
	 * Read-only.
	 */
	boolean containsTransport(T txn, TransportId t) throws DbException;

	/**
	 * Returns true if the database contains the given message, the message is
	 * shared, and the visibility of the message's group to the given contact
	 * is either {@link Visibility VISIBLE} or {@link Visibility SHARED}.
	 * <p/>
	 * Read-only.
	 */
	boolean containsVisibleMessage(T txn, ContactId c, MessageId m)
			throws DbException;

	/**
	 * Returns the number of messages offered by the given contact.
	 * <p/>
	 * Read-only.
	 */
	int countOfferedMessages(T txn, ContactId c) throws DbException;

	/**
	 * Deletes the message with the given ID. Unlike
	 * {@link #removeMessage(Object, MessageId)}, the message ID and any other
	 * associated data are not deleted, and
	 * {@link #containsMessage(Object, MessageId)} will continue to return true.
	 */
	void deleteMessage(T txn, MessageId m) throws DbException;

	/**
	 * Deletes any metadata associated with the given message.
	 */
	void deleteMessageMetadata(T txn, MessageId m) throws DbException;

	/**
	 * Returns the contact with the given ID.
	 * <p/>
	 * Read-only.
	 */
	Contact getContact(T txn, ContactId c) throws DbException;

	/**
	 * Returns all contacts.
	 * <p/>
	 * Read-only.
	 */
	Collection<Contact> getContacts(T txn) throws DbException;

	/**
	 * Returns a possibly empty collection of contacts with the given author ID.
	 * <p/>
	 * Read-only.
	 */
	Collection<Contact> getContactsByAuthorId(T txn, AuthorId remote)
			throws DbException;

	/**
	 * Returns all contacts associated with the given local pseudonym.
	 * <p/>
	 * Read-only.
	 */
	Collection<ContactId> getContacts(T txn, AuthorId a) throws DbException;

	/**
	 * Returns the amount of free storage space available to the database, in
	 * bytes. This is based on the minimum of the space available on the device
	 * where the database is stored and the database's configured size.
	 */
	long getFreeSpace() throws DbException;

	/**
	 * Returns the group with the given ID.
	 * <p/>
	 * Read-only.
	 */
	Group getGroup(T txn, GroupId g) throws DbException;

	/**
	 * Returns the metadata for the given group.
	 * <p/>
	 * Read-only.
	 */
	Metadata getGroupMetadata(T txn, GroupId g) throws DbException;

	/**
	 * Returns all groups belonging to the given client.
	 * <p/>
	 * Read-only.
	 */
	Collection<Group> getGroups(T txn, ClientId c) throws DbException;

	/**
	 * Returns the given group's visibility to the given contact, or
	 * {@link Visibility INVISIBLE} if the group is not in the database.
	 * <p/>
	 * Read-only.
	 */
	Visibility getGroupVisibility(T txn, ContactId c, GroupId g)
			throws DbException;

	/**
	 * Returns the IDs of all contacts to which the given group's visibility is
	 * either {@link Visibility VISIBLE} or {@link Visibility SHARED}.
	 * <p/>
	 * Read-only.
	 */
	Collection<ContactId> getGroupVisibility(T txn, GroupId g)
			throws DbException;

	/**
	 * Returns the local pseudonym with the given ID.
	 * <p/>
	 * Read-only.
	 */
	LocalAuthor getLocalAuthor(T txn, AuthorId a) throws DbException;

	/**
	 * Returns all local pseudonyms.
	 * <p/>
	 * Read-only.
	 */
	Collection<LocalAuthor> getLocalAuthors(T txn) throws DbException;

	/**
	 * Returns the IDs and states of all dependencies of the given message.
	 * Missing dependencies have the state {@link State UNKNOWN}.
	 * Dependencies in other groups have the state {@link State INVALID}.
	 * Note that these states are not set on the dependencies themselves; the
	 * returned states should only be taken in the context of the given message.
	 * <p/>
	 * Read-only.
	 */
	Map<MessageId, State> getMessageDependencies(T txn, MessageId m)
			throws DbException;

	/**
	 * Returns all IDs and states of all dependents of the given message.
	 * Messages in other groups that declare a dependency on the given message
	 * will be returned even though such dependencies are invalid.
	 * <p/>
	 * Read-only.
	 */
	Map<MessageId, State> getMessageDependents(T txn, MessageId m)
			throws DbException;

	/**
	 * Returns the IDs of all messages in the given group.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageId> getMessageIds(T txn, GroupId g) throws DbException;

	/**
	 * Returns the IDs of any messages in the given group with metadata
	 * matching all entries in the given query. If the query is empty, the IDs
	 * of all messages are returned.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageId> getMessageIds(T txn, GroupId g, Metadata query)
			throws DbException;

	/**
	 * Returns the metadata for all delivered messages in the given group.
	 * <p/>
	 * Read-only.
	 */
	Map<MessageId, Metadata> getMessageMetadata(T txn, GroupId g)
			throws DbException;

	/**
	 * Returns the metadata for any messages in the given group with metadata
	 * matching all entries in the given query. If the query is empty, the
	 * metadata for all messages is returned.
	 * <p/>
	 * Read-only.
	 */
	Map<MessageId, Metadata> getMessageMetadata(T txn, GroupId g,
			Metadata query) throws DbException;

	/**
	 * Returns the metadata for the given delivered message.
	 * <p/>
	 * Read-only.
	 */
	Metadata getMessageMetadataForValidator(T txn, MessageId m)
			throws DbException;

	/**
	 * Returns the metadata for the given message.
	 * <p/>
	 * Read-only.
	 */
	Metadata getMessageMetadata(T txn, MessageId m) throws DbException;

	/**
	 * Returns the validation and delivery state of the given message.
	 * <p/>
	 * Read-only.
	 */
	State getMessageState(T txn, MessageId m) throws DbException;

	/**
	 * Returns the status of all messages in the given group with respect
	 * to the given contact.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageStatus> getMessageStatus(T txn, ContactId c, GroupId g)
			throws DbException;

	/**
	 * Returns the status of the given message with respect to the given
	 * contact.
	 * <p/>
	 * Read-only.
	 */
	MessageStatus getMessageStatus(T txn, ContactId c, MessageId m)
			throws DbException;

	/**
	 * Returns the IDs of some messages received from the given contact that
	 * need to be acknowledged, up to the given number of messages.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageId> getMessagesToAck(T txn, ContactId c, int maxMessages)
			throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be offered to the
	 * given contact, up to the given number of messages.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageId> getMessagesToOffer(T txn, ContactId c,
			int maxMessages) throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be requested from
	 * the given contact, up to the given number of messages.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageId> getMessagesToRequest(T txn, ContactId c,
			int maxMessages) throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be sent to the
	 * given contact, up to the given total length.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageId> getMessagesToSend(T txn, ContactId c, int maxLength)
			throws DbException;

	/**
	 * Returns the IDs of any messages that need to be validated by the given
	 * client.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageId> getMessagesToValidate(T txn, ClientId c)
			throws DbException;

	/**
	 * Returns the IDs of any messages that are still pending due to
	 * dependencies to other messages for the given client.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageId> getPendingMessages(T txn, ClientId c)
			throws DbException;

	/**
	 * Returns the IDs of any messages from the given client
	 * that have a shared dependent, but are still not shared themselves.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageId> getMessagesToShare(T txn, ClientId c)
			throws DbException;

	/**
	 * Returns the message with the given ID, in serialised form, or null if
	 * the message has been deleted.
	 * <p/>
	 * Read-only.
	 */
	@Nullable
	byte[] getRawMessage(T txn, MessageId m) throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be sent to the
	 * given contact and have been requested by the contact, up to the given
	 * total length.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageId> getRequestedMessagesToSend(T txn, ContactId c,
			int maxLength) throws DbException;

	/**
	 * Returns all settings in the given namespace.
	 * <p/>
	 * Read-only.
	 */
	Settings getSettings(T txn, String namespace) throws DbException;

	/**
	 * Returns all transport keys for the given transport.
	 * <p/>
	 * Read-only.
	 */
	Map<ContactId, TransportKeys> getTransportKeys(T txn, TransportId t)
			throws DbException;

	/**
	 * Increments the outgoing stream counter for the given contact and
	 * transport in the given rotation period.
	 */
	void incrementStreamCounter(T txn, ContactId c, TransportId t,
			long rotationPeriod) throws DbException;

	/**
	 * Marks the given messages as not needing to be acknowledged to the
	 * given contact.
	 */
	void lowerAckFlag(T txn, ContactId c, Collection<MessageId> acked)
			throws DbException;

	/**
	 * Marks the given messages as not having been requested by the given
	 * contact.
	 */
	void lowerRequestedFlag(T txn, ContactId c, Collection<MessageId> requested)
			throws DbException;

	/**
	 * Merges the given metadata with the existing metadata for the given
	 * group.
	 */
	void mergeGroupMetadata(T txn, GroupId g, Metadata meta)
			throws DbException;

	/**
	 * Merges the given metadata with the existing metadata for the given
	 * message.
	 */
	void mergeMessageMetadata(T txn, MessageId m, Metadata meta)
			throws DbException;

	/**
	 * Merges the given settings with the existing settings in the given
	 * namespace.
	 */
	void mergeSettings(T txn, Settings s, String namespace) throws DbException;

	/**
	 * Marks a message as needing to be acknowledged to the given contact.
	 */
	void raiseAckFlag(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Marks a message as having been requested by the given contact.
	 */
	void raiseRequestedFlag(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Marks a message as having been seen by the given contact.
	 */
	void raiseSeenFlag(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Removes a contact from the database.
	 */
	void removeContact(T txn, ContactId c) throws DbException;

	/**
	 * Removes a group (and all associated state) from the database.
	 */
	void removeGroup(T txn, GroupId g) throws DbException;

	/**
	 * Sets the given group's visibility to the given contact to
	 * {@link Visibility INVISIBLE}.
	 */
	void removeGroupVisibility(T txn, ContactId c, GroupId g)
			throws DbException;

	/**
	 * Removes a local pseudonym (and all associated state) from the database.
	 */
	void removeLocalAuthor(T txn, AuthorId a) throws DbException;

	/**
	 * Removes a message (and all associated state) from the database.
	 */
	void removeMessage(T txn, MessageId m) throws DbException;

	/**
	 * Removes an offered message that was offered by the given contact, or
	 * returns false if there is no such message.
	 */
	boolean removeOfferedMessage(T txn, ContactId c, MessageId m)
			throws DbException;

	/**
	 * Removes the given offered messages that were offered by the given
	 * contact.
	 */
	void removeOfferedMessages(T txn, ContactId c,
			Collection<MessageId> requested) throws DbException;

	/**
	 * Removes the status of the given message with respect to the given
	 * contact.
	 */
	void removeStatus(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Removes a transport (and all associated state) from the database.
	 */
	void removeTransport(T txn, TransportId t) throws DbException;

	/**
	 * Resets the transmission count and expiry time of the given message with
	 * respect to the given contact.
	 */
	void resetExpiryTime(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Marks the given contact as verified.
	 */
	void setContactVerified(T txn, ContactId c) throws DbException;

	/**
	 * Marks the given contact as active or inactive.
	 */
	void setContactActive(T txn, ContactId c, boolean active)
			throws DbException;

	/**
	 * Sets the given group's visibility to the given contact to either
	 * {@link Visibility VISIBLE} or {@link Visibility SHARED}.
	 */
	void setGroupVisibility(T txn, ContactId c, GroupId g, boolean shared)
			throws DbException;

	/**
	 * Marks the given message as shared.
	 */
	void setMessageShared(T txn, MessageId m) throws DbException;

	/**
	 * Sets the validation and delivery state of the given message.
	 */
	void setMessageState(T txn, MessageId m, State state) throws DbException;

	/**
	 * Sets the reordering window for the given contact and transport in the
	 * given rotation period.
	 */
	void setReorderingWindow(T txn, ContactId c, TransportId t,
			long rotationPeriod, long base, byte[] bitmap) throws DbException;

	/**
	 * Updates the transmission count and expiry time of the given message
	 * with respect to the given contact, using the latency of the transport
	 * over which it was sent.
	 */
	void updateExpiryTime(T txn, ContactId c, MessageId m, int maxLatency)
			throws DbException;

	/**
	 * Stores the given transport keys, deleting any keys they have replaced.
	 */
	void updateTransportKeys(T txn, Map<ContactId, TransportKeys> keys)
			throws DbException;
}
