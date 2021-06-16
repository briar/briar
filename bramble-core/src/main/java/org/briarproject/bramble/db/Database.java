package org.briarproject.bramble.db;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DataTooNewException;
import org.briarproject.bramble.api.db.DataTooOldException;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.MessageDeletedException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.MigrationListener;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.Identity;
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
import org.briarproject.bramble.api.sync.validation.MessageState;
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.bramble.api.transport.TransportKeySet;
import org.briarproject.bramble.api.transport.TransportKeys;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A low-level interface to the database ({@link DatabaseComponent} provides a
 * high-level interface).
 * <p/>
 * Most operations take a transaction argument, which is obtained by calling
 * {@link #startTransaction()}. Every transaction must be terminated by calling
 * either {@link #abortTransaction(Object) abortTransaction(T)} or
 * {@link #commitTransaction(Object) commitTransaction(T)}, even if an
 * exception is thrown.
 */
@NotNullByDefault
interface Database<T> {

	/**
	 * Opens the database and returns true if the database already existed.
	 *
	 * @throws DataTooNewException if the data uses a newer schema than the
	 * current code
	 * @throws DataTooOldException if the data uses an older schema than the
	 * current code and cannot be migrated
	 */
	boolean open(SecretKey key, @Nullable MigrationListener listener)
			throws DbException;

	/**
	 * Prevents new transactions from starting, waits for all current
	 * transactions to finish, and closes the database.
	 */
	void close() throws DbException;

	/**
	 * Returns true if the dirty flag was set while opening the database,
	 * indicating that the database has not been shut down properly the last
	 * time it was closed and some data could be lost.
	 */
	boolean wasDirtyOnInitialisation();

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
	ContactId addContact(T txn, Author remote, AuthorId local,
			@Nullable PublicKey handshake, boolean verified) throws DbException;

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
	 * Stores an identity.
	 */
	void addIdentity(T txn, Identity i) throws DbException;

	/**
	 * Stores a message.
	 *
	 * @param sender the contact from whom the message was received, or null
	 * if the message was created locally.
	 */
	void addMessage(T txn, Message m, MessageState state, boolean shared,
			boolean temporary, @Nullable ContactId sender) throws DbException;

	/**
	 * Adds a dependency between two messages, where the dependent message is
	 * in the given state.
	 */
	void addMessageDependency(T txn, Message dependent, MessageId dependency,
			MessageState dependentState) throws DbException;

	/**
	 * Records that a message has been offered by the given contact.
	 */
	void addOfferedMessage(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Stores a pending contact.
	 */
	void addPendingContact(T txn, PendingContact p) throws DbException;

	/**
	 * Stores a transport.
	 */
	void addTransport(T txn, TransportId t, int maxLatency)
			throws DbException;

	/**
	 * Stores the given transport keys for the given contact and returns a
	 * key set ID.
	 */
	KeySetId addTransportKeys(T txn, ContactId c, TransportKeys k)
			throws DbException;

	/**
	 * Stores the given transport keys for the given pending contact and
	 * returns a key set ID.
	 */
	KeySetId addTransportKeys(T txn, PendingContactId p, TransportKeys k)
			throws DbException;

	/**
	 * Returns true if there are any acks or messages to send to the given
	 * contact over a transport with the given maximum latency.
	 * <p/>
	 * Read-only.
	 *
	 * @param eager True if messages that are not yet due for retransmission
	 * should be included
	 */
	boolean containsAnythingToSend(T txn, ContactId c, int maxLatency,
			boolean eager) throws DbException;

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
	 * Returns true if the database contains an identity for the given
	 * pseudonym.
	 * <p/>
	 * Read-only.
	 */
	boolean containsIdentity(T txn, AuthorId a) throws DbException;

	/**
	 * Returns true if the database contains the given message.
	 * <p/>
	 * Read-only.
	 */
	boolean containsMessage(T txn, MessageId m) throws DbException;

	/**
	 * Returns true if the database contains the given pending contact.
	 * <p/>
	 * Read-only.
	 */
	boolean containsPendingContact(T txn, PendingContactId p)
			throws DbException;

	/**
	 * Returns true if the database contains the given transport.
	 * <p/>
	 * Read-only.
	 */
	boolean containsTransport(T txn, TransportId t) throws DbException;

	/**
	 * Returns true if the database contains keys for communicating with the
	 * given contact over the given transport. Handshake mode and rotation mode
	 * keys are included, whether activated or not.
	 * <p/>
	 * Read-only.
	 */
	boolean containsTransportKeys(T txn, ContactId c, TransportId t)
			throws DbException;

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
	Collection<ContactId> getContacts(T txn, AuthorId local) throws DbException;

	/**
	 * Returns the contact with the given {@code handshakePublicKey}
	 * for the given local pseudonym or {@code null} if none exists.
	 * <p/>
	 * Read-only.
	 */
	@Nullable
	Contact getContact(T txn, PublicKey handshakePublicKey, AuthorId local)
			throws DbException;

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
	Collection<Group> getGroups(T txn, ClientId c, int majorVersion)
			throws DbException;

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
	Map<ContactId, Boolean> getGroupVisibility(T txn, GroupId g)
			throws DbException;

	/**
	 * Returns the identity for local pseudonym with the given ID.
	 * <p/>
	 * Read-only.
	 */
	Identity getIdentity(T txn, AuthorId a) throws DbException;

	/**
	 * Returns the identities for all local pseudonyms.
	 * <p/>
	 * Read-only.
	 */
	Collection<Identity> getIdentities(T txn) throws DbException;

	/**
	 * Returns the message with the given ID.
	 * <p/>
	 * Read-only.
	 *
	 * @throws MessageDeletedException if the message has been deleted
	 */
	Message getMessage(T txn, MessageId m) throws DbException;

	/**
	 * Returns the IDs and states of all dependencies of the given message.
	 * For missing dependencies and dependencies in other groups, the state
	 * {@link MessageState UNKNOWN} is returned.
	 * <p/>
	 * Read-only.
	 */
	Map<MessageId, MessageState> getMessageDependencies(T txn, MessageId m)
			throws DbException;

	/**
	 * Returns the IDs and states of all dependents of the given message.
	 * Dependents in other groups are not returned. If the given message is
	 * missing, no dependents are returned.
	 * <p/>
	 * Read-only.
	 */
	Map<MessageId, MessageState> getMessageDependents(T txn, MessageId m)
			throws DbException;

	/**
	 * Returns the IDs of all delivered messages in the given group.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageId> getMessageIds(T txn, GroupId g) throws DbException;

	/**
	 * Returns the IDs of any delivered messages in the given group with
	 * metadata that matches all entries in the given query. If the query is
	 * empty, the IDs of all delivered messages are returned.
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
	 * Returns the metadata for any delivered messages in the given group with
	 * metadata that matches all entries in the given query. If the query is
	 * empty, the metadata for all delivered messages is returned.
	 * <p/>
	 * Read-only.
	 */
	Map<MessageId, Metadata> getMessageMetadata(T txn, GroupId g,
			Metadata query) throws DbException;

	/**
	 * Returns the metadata for the given delivered or pending message.
	 * This is only meant to be used by the ValidationManager.
	 * <p/>
	 * Read-only.
	 */
	Metadata getMessageMetadataForValidator(T txn, MessageId m)
			throws DbException;

	/**
	 * Returns the metadata for the given delivered message.
	 * <p/>
	 * Read-only.
	 */
	Metadata getMessageMetadata(T txn, MessageId m) throws DbException;

	/**
	 * Returns the validation and delivery state of the given message.
	 * <p/>
	 * Read-only.
	 */
	MessageState getMessageState(T txn, MessageId m) throws DbException;

	/**
	 * Returns the status of all delivered messages in the given group with
	 * respect to the given contact.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageStatus> getMessageStatus(T txn, ContactId c, GroupId g)
			throws DbException;

	/**
	 * Returns the status of the given delivered message with respect to the
	 * given contact, or null if the message's group is invisible to the
	 * contact.
	 * <p/>
	 * Read-only.
	 */
	@Nullable
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
			int maxMessages, int maxLatency) throws DbException;

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
	 * Unlike {@link #getUnackedMessagesToSend(Object, ContactId)} this method
	 * does not return messages that have already been sent unless they are
	 * due for retransmission.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageId> getMessagesToSend(T txn, ContactId c, int maxLength,
			int maxLatency) throws DbException;

	/**
	 * Returns the IDs of all messages that are eligible to be sent to the
	 * given contact, together with their raw lengths.
	 * <p/>
	 * Unlike {@link #getMessagesToSend(Object, ContactId, int, int)} this
	 * method may return messages that have already been sent and are not yet
	 * due for retransmission.
	 * <p/>
	 * Read-only.
	 */
	Map<MessageId, Integer> getUnackedMessagesToSend(T txn, ContactId c)
			throws DbException;

	/**
	 * Returns the total length, including headers, of all messages that are
	 * eligible to be sent to the given contact. This may include messages
	 * that have already been sent and are not yet due for retransmission.
	 * <p/>
	 * Read-only.
	 */
	long getUnackedMessageBytesToSend(T txn, ContactId c) throws DbException;

	/**
	 * Returns the IDs of any messages that need to be validated.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageId> getMessagesToValidate(T txn) throws DbException;

	/**
	 * Returns the IDs of any messages that are pending delivery due to
	 * dependencies on other messages.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageId> getPendingMessages(T txn) throws DbException;

	/**
	 * Returns the IDs of any messages that have a shared dependent but have
	 * not yet been shared themselves.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageId> getMessagesToShare(T txn) throws DbException;

	/**
	 * Returns the IDs of any messages of any messages that are due for
	 * deletion, along with their group IDs.
	 * <p/>
	 * Read-only.
	 */
	Map<GroupId, Collection<MessageId>> getMessagesToDelete(T txn)
			throws DbException;

	/**
	 * Returns the next time (in milliseconds since the Unix epoch) when a
	 * message is due to be deleted, or
	 * {@link DatabaseComponent#NO_CLEANUP_DEADLINE} if no messages are
	 * scheduled to be deleted.
	 * <p/>
	 * Read-only.
	 */
	long getNextCleanupDeadline(T txn) throws DbException;

	/**
	 * Returns the next time (in milliseconds since the Unix epoch) when a
	 * message is due to be sent to the given contact. The returned value may
	 * be zero if a message is due to be sent immediately, or Long.MAX_VALUE
	 * if no messages are scheduled to be sent.
	 * <p/>
	 * Read-only.
	 */
	long getNextSendTime(T txn, ContactId c) throws DbException;

	/**
	 * Returns the pending contact with the given ID.
	 * <p/>
	 * Read-only.
	 */
	PendingContact getPendingContact(T txn, PendingContactId p)
			throws DbException;

	/**
	 * Returns all pending contacts.
	 * <p/>
	 * Read-only.
	 */
	Collection<PendingContact> getPendingContacts(T txn) throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be sent to the
	 * given contact and have been requested by the contact, up to the given
	 * total length.
	 * <p/>
	 * Read-only.
	 */
	Collection<MessageId> getRequestedMessagesToSend(T txn, ContactId c,
			int maxLength, int maxLatency) throws DbException;

	/**
	 * Returns all settings in the given namespace.
	 * <p/>
	 * Read-only.
	 */
	Settings getSettings(T txn, String namespace) throws DbException;

	/**
	 * Returns the versions of the sync protocol supported by the given contact.
	 * <p/>
	 * Read-only.
	 */
	List<Byte> getSyncVersions(T txn, ContactId c) throws DbException;

	/**
	 * Returns all transport keys for the given transport.
	 * <p/>
	 * Read-only.
	 */
	Collection<TransportKeySet> getTransportKeys(T txn, TransportId t)
			throws DbException;

	/**
	 * Returns the contact IDs and transport IDs for which the DB contains
	 * at least one set of transport keys. Handshake mode and rotation mode
	 * keys are included, whether activated or not.
	 * <p/>
	 * Read-only.
	 */
	Map<ContactId, Collection<TransportId>> getTransportsWithKeys(T txn)
			throws DbException;

	/**
	 * Increments the outgoing stream counter for the given transport keys.
	 */
	void incrementStreamCounter(T txn, TransportId t, KeySetId k)
			throws DbException;

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
	 *
	 * @return True if the message was not already marked as seen.
	 */
	boolean raiseSeenFlag(T txn, ContactId c, MessageId m) throws DbException;

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
	 * Removes an identity (and all associated state) from the database.
	 */
	void removeIdentity(T txn, AuthorId a) throws DbException;

	/**
	 * Removes a message (and all associated state) from the database.
	 */
	void removeMessage(T txn, MessageId m) throws DbException;

	/**
	 * Removes the given offered messages that were offered by the given
	 * contact.
	 */
	void removeOfferedMessages(T txn, ContactId c,
			Collection<MessageId> requested) throws DbException;

	/**
	 * Removes a pending contact (and all associated state) from the database.
	 */
	void removePendingContact(T txn, PendingContactId p) throws DbException;

	/**
	 * Removes all temporary messages (and all associated state) from the
	 * database.
	 */
	void removeTemporaryMessages(T txn) throws DbException;

	/**
	 * Removes a transport (and all associated state) from the database.
	 */
	void removeTransport(T txn, TransportId t) throws DbException;

	/**
	 * Removes the given transport keys from the database.
	 */
	void removeTransportKeys(T txn, TransportId t, KeySetId k)
			throws DbException;

	/**
	 * Resets the transmission count and expiry time of the given message with
	 * respect to the given contact.
	 */
	void resetExpiryTime(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Sets the cleanup timer duration for the given message. This does not
	 * start the message's cleanup timer.
	 */
	void setCleanupTimerDuration(T txn, MessageId m, long duration)
			throws DbException;

	/**
	 * Marks the given contact as verified.
	 */
	void setContactVerified(T txn, ContactId c) throws DbException;

	/**
	 * Sets an alias name for a contact.
	 */
	void setContactAlias(T txn, ContactId c, @Nullable String alias)
			throws DbException;

	/**
	 * Sets the given group's visibility to the given contact to either
	 * {@link Visibility VISIBLE} or {@link Visibility SHARED}.
	 */
	void setGroupVisibility(T txn, ContactId c, GroupId g, boolean shared)
			throws DbException;

	/**
	 * Sets the handshake key pair for the identity with the given ID.
	 */
	void setHandshakeKeyPair(T txn, AuthorId local, PublicKey publicKey,
			PrivateKey privateKey) throws DbException;

	/**
	 * Marks the given message as permanent, i.e. not temporary.
	 */
	void setMessagePermanent(T txn, MessageId m) throws DbException;

	/**
	 * Marks the given message as shared or not.
	 */
	void setMessageShared(T txn, MessageId m, boolean shared)
			throws DbException;

	/**
	 * Sets the validation and delivery state of the given message.
	 */
	void setMessageState(T txn, MessageId m, MessageState state)
			throws DbException;

	/**
	 * Sets the reordering window for the given transport keys in the given
	 * time period.
	 */
	void setReorderingWindow(T txn, KeySetId k, TransportId t,
			long timePeriod, long base, byte[] bitmap) throws DbException;

	/**
	 * Sets the versions of the sync protocol supported by the given contact.
	 */
	void setSyncVersions(T txn, ContactId c, List<Byte> supported)
			throws DbException;

	/**
	 * Marks the given transport keys as usable for outgoing streams.
	 */
	void setTransportKeysActive(T txn, TransportId t, KeySetId k)
			throws DbException;

	/**
	 * Starts the cleanup timer for the given message, if a timer duration
	 * has been set and the timer has not already been started.
	 *
	 * @return The cleanup deadline, or
	 * {@link DatabaseComponent#TIMER_NOT_STARTED} if no timer duration has
	 * been set for this message or its timer has already been started.
	 */
	long startCleanupTimer(T txn, MessageId m) throws DbException;

	/**
	 * Stops the cleanup timer for the given message, if the timer has been
	 * started.
	 */
	void stopCleanupTimer(T txn, MessageId m) throws DbException;

	/**
	 * Updates the transmission count, expiry time and estimated time of arrival
	 * of the given message with respect to the given contact, using the latency
	 * of the transport over which it was sent.
	 */
	void updateExpiryTimeAndEta(T txn, ContactId c, MessageId m, int maxLatency)
			throws DbException;

	/**
	 * Stores the given transport keys, deleting any keys they have replaced.
	 */
	void updateTransportKeys(T txn, TransportKeySet ks) throws DbException;
}
