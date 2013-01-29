package net.sf.briar.db;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.MessageHeader;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.RetentionAck;
import net.sf.briar.api.protocol.RetentionUpdate;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.SubscriptionAck;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportAck;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.transport.ContactTransport;
import net.sf.briar.api.transport.TemporarySecret;

/**
 * A low-level interface to the database (DatabaseComponent provides a
 * high-level interface). Most operations take a transaction argument, which is
 * obtained by calling {@link #startTransaction()}. Every transaction must be
 * terminated by calling either {@link #abortTransaction(T)} or
 * {@link #commitTransaction(T)}, even if an exception is thrown.
 * <p>
 * Locking is provided by the DatabaseComponent implementation. To prevent
 * deadlock, locks must be acquired in the following (alphabetical) order:
 * <ul>
 * <li> contact
 * <li> message
 * <li> rating
 * <li> retention
 * <li> subscription
 * <li> transport
 * <li> window
 * </ul>
 */
interface Database<T> {

	/**
	 * Opens the database.
	 * @param resume true to reopen an existing database, false to create a
	 * new one.
	 */
	void open(boolean resume) throws DbException, IOException;

	/**
	 * Prevents new transactions from starting, waits for all current
	 * transactions to finish, and closes the database.
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
	 * Adds a new contact to the database and returns an ID for the contact.
	 * <p>
	 * Locking: contact write, subscription write.
	 */
	ContactId addContact(T txn) throws DbException;

	/**
	 * Adds a contact transport to the database.
	 * <p>
	 * Locking: contact read, transport read, window write.
	 */
	void addContactTransport(T txn, ContactTransport ct) throws DbException;

	/**
	 * Stores the given message, or returns false if the message is already in
	 * the database.
	 * <p>
	 * Locking: message write.
	 */
	boolean addGroupMessage(T txn, Message m) throws DbException;

	/**
	 * Records a received message as needing to be acknowledged.
	 * <p>
	 * Locking: contact read, message write.
	 */
	void addMessageToAck(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Records a collection of sent messages as needing to be acknowledged.
	 * <p>
	 * Locking: contact read, message write.
	 */
	void addOutstandingMessages(T txn, ContactId c, Collection<MessageId> sent)
			throws DbException;

	/**
	 * Stores the given message, or returns false if the message is already in
	 * the database.
	 * <p>
	 * Locking: contact read, message write.
	 */
	boolean addPrivateMessage(T txn, Message m, ContactId c) throws DbException;

	/**
	 * Stores the given temporary secrets and deletes any secrets that have
	 * been made obsolete.
	 * <p>
	 * Locking: contact read, transport read, window write.
	 */
	void addSecrets(T txn, Collection<TemporarySecret> secrets)
			throws DbException;

	/**
	 * Subscribes to the given group.
	 * <p>
	 * Locking: subscription write.
	 */
	void addSubscription(T txn, Group g) throws DbException;

	/**
	 * Adds a new transport to the database.
	 * <p>
	 * Locking: transport write.
	 */
	void addTransport(T txn, TransportId t) throws DbException;

	/**
	 * Makes the given group visible to the given contact.
	 * <p>
	 * Locking: contact write, subscription write.
	 */
	void addVisibility(T txn, ContactId c, GroupId g) throws DbException;

	/**
	 * Returns true if the database contains the given contact.
	 * <p>
	 * Locking: contact read.
	 */
	boolean containsContact(T txn, ContactId c) throws DbException;

	/**
	 * Returns true if the database contains the given contact transport.
	 * <p>
	 * Locking: contact read, transport read, window read.
	 */
	boolean containsContactTransport(T txn, ContactId c, TransportId t)
			throws DbException;

	/**
	 * Returns true if the database contains the given message.
	 * <p>
	 * Locking: message read.
	 */
	boolean containsMessage(T txn, MessageId m) throws DbException;

	/**
	 * Returns true if the user subscribes to the given group.
	 * <p>
	 * Locking: subscription read.
	 */
	boolean containsSubscription(T txn, GroupId g) throws DbException;

	/**
	 * Returns true if the user subscribes to the given group and the
	 * subscription is visible to the given contact.
	 * <p>
	 * Locking: contact read, subscription read.
	 */
	boolean containsVisibleSubscription(T txn, ContactId c, GroupId g)
			throws DbException;

	/**
	 * Returns the configuration for the given transport.
	 * <p>
	 * Locking: transport read.
	 */
	TransportConfig getConfig(T txn, TransportId t) throws DbException;

	/**
	 * Returns the IDs of all contacts.
	 * <p>
	 * Locking: contact read.
	 */
	Collection<ContactId> getContacts(T txn) throws DbException;

	/**
	 * Returns all contact transports.
	 * <p>
	 * Locking: contact read, transport read, window read.
	 */
	Collection<ContactTransport> getContactTransports(T txn) throws DbException;

	/**
	 * Returns the amount of free storage space available to the database, in
	 * bytes. This is based on the minimum of the space available on the device
	 * where the database is stored and the database's configured size.
	 * <p>
	 * Locking: message read.
	 */
	long getFreeSpace() throws DbException;

	/**
	 * Returns the parent of the given group message, or null if either the
	 * message has no parent, or the parent is absent from the database, or the
	 * parent belongs to a different group.
	 * <p>
	 * Locking: message read.
	 */
	MessageId getGroupMessageParent(T txn, MessageId m) throws DbException;

	/**
	 * Returns the local transport properties for the given transport.
	 * <p>
	 * Locking: transport read.
	 */
	TransportProperties getLocalProperties(T txn, TransportId t)
			throws DbException;

	/**
	 * Returns the message identified by the given ID, in serialised form.
	 * <p>
	 * Locking: message read.
	 */
	byte[] getMessage(T txn, MessageId m) throws DbException;

	/**
	 * Returns the body of the message identified by the given ID.
	 * <p>
	 * Locking: message read.
	 */
	byte[] getMessageBody(T txn, MessageId m) throws DbException;

	/**
	 * Returns the headers of all messages in the given group.
	 * <p>
	 * Locking: message read.
	 */
	Collection<MessageHeader> getMessageHeaders(T txn, GroupId g)
			throws DbException;

	/**
	 * Returns the message identified by the given ID, in raw format, or null
	 * if the message is not present in the database or is not sendable to the
	 * given contact.
	 * <p>
	 * Locking: contact read, message read, subscription read.
	 */
	byte[] getMessageIfSendable(T txn, ContactId c, MessageId m)
			throws DbException;

	/**
	 * Returns the IDs of all messages signed by the given author.
	 * <p>
	 * Locking: message read.
	 */
	Collection<MessageId> getMessagesByAuthor(T txn, AuthorId a)
			throws DbException;

	/**
	 * Returns the IDs of some messages received from the given contact that
	 * need to be acknowledged, up to the given number of messages.
	 * <p>
	 * Locking: contact read, message read.
	 */
	Collection<MessageId> getMessagesToAck(T txn, ContactId c, int maxMessages)
			throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be sent to the
	 * given contact, up to the given number of messages.
	 * <p>
	 * Locking: contact read, message read, subscription read.
	 */
	Collection<MessageId> getMessagesToOffer(T txn, ContactId c,
			int maxMessages) throws DbException;

	/**
	 * Returns the number of children of the message identified by the given
	 * ID that are present in the database and have sendability scores greater
	 * than zero.
	 * <p>
	 * Locking: message read.
	 */
	int getNumberOfSendableChildren(T txn, MessageId m) throws DbException;

	/**
	 * Returns the IDs of the oldest messages in the database, with a total
	 * size less than or equal to the given size.
	 * <p>
	 * Locking: message read.
	 */
	Collection<MessageId> getOldMessages(T txn, int size) throws DbException;

	/**
	 * Returns the user's rating for the given author.
	 * <p>
	 * Locking: rating read.
	 */
	Rating getRating(T txn, AuthorId a) throws DbException;

	/**
	 * Returns true if the given message has been read.
	 * <p>
	 * Locking: message read.
	 */
	boolean getReadFlag(T txn, MessageId m) throws DbException;

	/**
	 * Returns all remote properties for the given transport.
	 * <p>
	 * Locking: contact read, transport read.
	 */
	Map<ContactId, TransportProperties> getRemoteProperties(T txn,
			TransportId t) throws DbException;

	/**
	 * Returns a retention ack for the given contact, or null if no ack is due.
	 * <p>
	 * Locking: contact read, retention write.
	 */
	RetentionAck getRetentionAck(T txn, ContactId c) throws DbException;

	/**
	 * Returns a retention update for the given contact, or null if no update
	 * is due.
	 * <p>
	 * Locking: contact read, retention write.
	 */
	RetentionUpdate getRetentionUpdate(T txn, ContactId c) throws DbException;

	/**
	 * Returns all temporary secrets.
	 * <p>
	 * Locking: contact read, transport read, window read.
	 */
	Collection<TemporarySecret> getSecrets(T txn) throws DbException;

	/**
	 * Returns the sendability score of the given group message.
	 * <p>
	 * Locking: message read.
	 */
	int getSendability(T txn, MessageId m) throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be sent to the
	 * given contact, with a total length less than or equal to the given
	 * length.
	 * <p>
	 * Locking: contact read, message read, subscription read.
	 */
	Collection<MessageId> getSendableMessages(T txn, ContactId c, int maxLength)
			throws DbException;

	/**
	 * Returns true if the given message has been starred.
	 * <p>
	 * Locking: message read.
	 */
	boolean getStarredFlag(T txn, MessageId m) throws DbException;

	/**
	 * Returns the groups to which the user subscribes.
	 * <p>
	 * Locking: subscription read.
	 */
	Collection<Group> getSubscriptions(T txn) throws DbException;

	/**
	 * Returns the groups to which the given contact subscribes.
	 * <p>
	 * Locking: contact read, subscription read.
	 */
	Collection<Group> getSubscriptions(T txn, ContactId c) throws DbException;

	/**
	 * Returns a subscription ack for the given contact, or null if no ack is
	 * due.
	 * <p>
	 * Locking: contact read, subscription write.
	 */
	SubscriptionAck getSubscriptionAck(T txn, ContactId c) throws DbException;

	/**
	 * Returns a subscription update for the given contact, or null if no
	 * update is due.
	 * <p>
	 * Locking: contact read, subscription write.
	 */
	SubscriptionUpdate getSubscriptionUpdate(T txn, ContactId c)
			throws DbException;

	/**
	 * Returns a collection of transport acks for the given contact, or null if
	 * no acks are due.
	 * <p>
	 * Locking: contact read, transport write.
	 */
	Collection<TransportAck> getTransportAcks(T txn, ContactId c)
			throws DbException;

	/**
	 * Returns a collection of transport updates for the given contact, or
	 * null if no updates are due.
	 * <p>
	 * Locking: contact read, transport write.
	 */
	Collection<TransportUpdate> getTransportUpdates(T txn, ContactId c)
			throws DbException;

	/**
	 * Returns the version number of the 
	/**
	 * Returns the number of unread messages in each subscribed group.
	 * <p>
	 * Locking: message read, subscription read.
	 */
	Map<GroupId, Integer> getUnreadMessageCounts(T txn) throws DbException;

	/**
	 * Returns the contacts to which the given group is visible.
	 * <p>
	 * Locking: contact read, subscription read.
	 */
	Collection<ContactId> getVisibility(T txn, GroupId g) throws DbException;

	/**
	 * Returns true if any messages are sendable to the given contact.
	 * <p>
	 * Locking: contact read, message read.
	 */
	boolean hasSendableMessages(T txn, ContactId c) throws DbException;

	/**
	 * Increments the outgoing connection counter for the given contact
	 * transport in the given rotation period and returns the old value;
	 * <p>
	 * Locking: contact read, transport read, window write.
	 */
	long incrementConnectionCounter(T txn, ContactId c, TransportId t,
			long period) throws DbException;

	/**
	 * Increments the retention time versions for all contacts to indicate that
	 * the database's retention time has changed and updates should be sent.
	 * <p>
	 * Locking: contact read, retention write.
	 */
	void incrementRetentionVersions(T txn) throws DbException;

	/**
	 * Merges the given configuration with the existing configuration for the
	 * given transport.
	 * <p>
	 * Locking: transport write.
	 */
	void mergeConfig(T txn, TransportId t, TransportConfig config)
			throws DbException;

	/**
	 * Merges the given properties with the existing local properties for the
	 * given transport.
	 * <p>
	 * Locking: transport write.
	 */
	void mergeLocalProperties(T txn, TransportId t, TransportProperties p)
			throws DbException;

	/**
	 * Removes a contact (and all associated state) from the database.
	 * <p>
	 * Locking: contact write, message write, subscription write,
	 * transport write, window write.
	 */
	void removeContact(T txn, ContactId c) throws DbException;

	/**
	 * Removes a message (and all associated state) from the database.
	 * <p>
	 * Locking: contact read, message write.
	 */
	void removeMessage(T txn, MessageId m) throws DbException;

	/**
	 * Marks the given messages received from the given contact as having been
	 * acknowledged.
	 * <p>
	 * Locking: contact read, message write.
	 */
	void removeMessagesToAck(T txn, ContactId c, Collection<MessageId> acked)
			throws DbException;

	/**
	 * Removes outstanding messages that have been acknowledged. Any of the
	 * messages that are still considered outstanding (Status.SENT) with
	 * respect to the given contact are now considered seen (Status.SEEN).
	 * <p>
	 * Locking: contact read, message write.
	 */
	void removeOutstandingMessages(T txn, ContactId c,
			Collection<MessageId> acked) throws DbException;

	/**
	 * Unsubscribes from the given group. Any messages belonging to the group
	 * are deleted from the database.
	 * <p>
	 * Locking: contact write, message write, subscription write.
	 */
	void removeSubscription(T txn, GroupId g) throws DbException;

	/**
	 * Removes a transport (and all associated state) from the database.
	 * <p>
	 * Locking: transport write.
	 */
	void removeTransport(T txn, TransportId t) throws DbException;

	/**
	 * Makes the given group invisible to the given contact.
	 * <p>
	 * Locking: contact write, subscription write.
	 */
	void removeVisibility(T txn, ContactId c, GroupId g) throws DbException;

	/**
	 * Sets the connection reordering window for the given contact transport in
	 * the given rotation period.
	 * <p>
	 * Locking: contact read, transport read, window write.
	 */
	void setConnectionWindow(T txn, ContactId c, TransportId t, long period,
			long centre, byte[] bitmap) throws DbException;

	/**
	 * Sets the user's rating for the given author.
	 * <p>
	 * Locking: rating write.
	 */
	Rating setRating(T txn, AuthorId a, Rating r) throws DbException;

	/**
	 * Marks the given message read or unread and returns true if it was
	 * previously read.
	 * <p>
	 * Locking: message write.
	 */
	boolean setRead(T txn, MessageId m, boolean read) throws DbException;

	/**
	 * Updates the remote transport properties for the given contact and the
	 * given transport, replacing any existing properties, unless an update
	 * with an equal or higher version number has already been received from
	 * the contact.
	 * <p>
	 * Locking: contact read, transport write.
	 */
	void setRemoteProperties(T txn, ContactId c, TransportUpdate u)
			throws DbException;

	/**
	 * Sets the retention time of the given contact's database, unless an
	 * update with an equal or higher version number has already been received
	 * from the contact.
	 * <p>
	 * Locking: contact read, retention write.
	 */
	void setRetentionTime(T txn, ContactId c, long retention, long version)
			throws DbException;

	/**
	 * Sets the sendability score of the given message.
	 * <p>
	 * Locking: message write.
	 */
	void setSendability(T txn, MessageId m, int sendability) throws DbException;

	/**
	 * Marks the given message starred or unstarred and returns true if it was
	 * previously starred.
	 * <p>
	 * Locking: message write.
	 */
	boolean setStarred(T txn, MessageId m, boolean starred) throws DbException;

	/**
	 * Sets the status of the given message with respect to the given contact.
	 * <p>
	 * Locking: contact read, message write.
	 */
	void setStatus(T txn, ContactId c, MessageId m, Status s)
			throws DbException;

	/**
	 * If the database contains the given message and it belongs to a group
	 * that is visible to the given contact, sets the status of the message
	 * with respect to the contact to Status.SEEN and returns true; otherwise
	 * returns false.
	 * <p>
	 * Locking: contact read, message write, subscription read.
	 */
	boolean setStatusSeenIfVisible(T txn, ContactId c, MessageId m)
			throws DbException;

	/**
	 * Updates the groups to which the given contact subscribes, unless an
	 * update with an equal or higher version number has already been received
	 * from the contact.
	 * <p>
	 * Locking: contact read, subscription write.
	 */
	void setSubscriptions(T txn, ContactId c, SubscriptionUpdate u)
			throws DbException;

	/**
	 * Records a retention ack from the given contact for the given version
	 * unless the contact has already acked an equal or higher version.
	 * <p>
	 * Locking: contact read, retention write.
	 */
	void setRetentionUpdateAcked(T txn, ContactId c, long version)
			throws DbException;

	/**
	 * Records a subscription ack from the given contact for the given version
	 * unless the contact has already acked an equal or higher version.
	 * <p>
	 * Locking: contact read, subscription write.
	 */
	void setSubscriptionUpdateAcked(T txn, ContactId c, long version)
			throws DbException;

	/**
	 * Records a transport ack from the give contact for the given version
	 * unless the contact has already acked an equal or higher version.
	 * <p>
	 * Locking: contact read, transport write.
	 */
	void setTransportUpdateAcked(T txn, ContactId c, TransportId t,
			long version) throws DbException;
}
