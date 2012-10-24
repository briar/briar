package net.sf.briar.db;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.db.ContactTransport;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.MessageHeader;
import net.sf.briar.api.db.Status;
import net.sf.briar.api.db.TemporarySecret;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;

/**
 * A low-level interface to the database (DatabaseComponent provides a
 * high-level interface). Most operations take a transaction argument, which is
 * obtained by calling startTransaction(). Every transaction must be
 * terminated by calling either abortTransaction() or commitTransaction(),
 * even if an exception is thrown.
 * <p>
 * Locking is provided by the DatabaseComponent implementation. To prevent
 * deadlock, locks must be acquired in the following order:
 * <ul>
 * <li> contact
 * <li> message
 * <li> messageFlag
 * <li> messageStatus
 * <li> rating
 * <li> subscription
 * <li> transport
 * <li> window
 * </ul>
 */
interface Database<T> {

	/**
	 * Opens the database.
	 * @param resume True to reopen an existing database, false to create a
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
	 * Records a received batch as needing to be acknowledged.
	 * <p>
	 * Locking: contact read, messageStatus write.
	 */
	void addBatchToAck(T txn, ContactId c, BatchId b) throws DbException;

	/**
	 * Adds a new contact to the database and returns an ID for the contact.
	 * <p>
	 * Locking: contact write, subscription write, transport write.
	 */
	ContactId addContact(T txn) throws DbException;

	/**
	 * Adds a contact transport to the database.
	 * <p>
	 * Locking: contact read, window write.
	 */
	void addContactTransport(T txn, ContactTransport ct) throws DbException;

	/**
	 * Returns false if the given message is already in the database. Otherwise
	 * stores the message and returns true.
	 * <p>
	 * Locking: message write.
	 */
	boolean addGroupMessage(T txn, Message m) throws DbException;

	/**
	 * Records a sent batch as needing to be acknowledged.
	 * <p>
	 * Locking: contact read, message read, messageStatus write.
	 */
	void addOutstandingBatch(T txn, ContactId c, BatchId b,
			Collection<MessageId> sent) throws DbException;

	/**
	 * Returns false if the given message is already in the database. Otherwise
	 * stores the message and returns true.
	 * <p>
	 * Locking: contact read, message write.
	 */
	boolean addPrivateMessage(T txn, Message m, ContactId c) throws DbException;

	/**
	 * Stores the given temporary secrets and deletes any secrets that have
	 * been made obsolete.
	 * <p>
	 * Locking: contact read, window write.
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
	 * Records the given contact's subscription to the given group starting at
	 * the given time.
	 * <p>
	 * Locking: contact read, subscription write.
	 */
	void addSubscription(T txn, ContactId c, Group g, long start)
			throws DbException;

	/**
	 * Makes the given group visible to the given contact.
	 * <p>
	 * Locking: contact read, subscription write.
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
	 * Locking: contact read, window read.
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
	 * Returns true if the user has been subscribed to the given group since
	 * the given time.
	 * <p>
	 * Locking: subscription read.
	 */
	boolean containsSubscription(T txn, GroupId g, long time)
			throws DbException;

	/**
	 * Returns true if the user is subscribed to the given group, the group is
	 * visible to the given contact, and the subscription has existed since the
	 * given time.
	 * <p>
	 * Locking: contact read, subscription read.
	 */
	boolean containsVisibleSubscription(T txn, GroupId g, ContactId c,
			long time) throws DbException;

	/**
	 * Returns the IDs of any batches received from the given contact that need
	 * to be acknowledged.
	 * <p>
	 * Locking: contact read, messageStatus read.
	 */
	Collection<BatchId> getBatchesToAck(T txn, ContactId c, int maxBatches)
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
	 * Locking: contact read, window read.
	 */
	Collection<ContactTransport> getContactTransports(T txn) throws DbException;

	/**
	 * Returns the approximate expiry time of the database.
	 * <p>
	 * Locking: message read.
	 */
	long getExpiryTime(T txn) throws DbException;

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
	 * Returns all local transports.
	 * <p>
	 * Locking: transport read.
	 */
	Collection<Transport> getLocalTransports(T txn) throws DbException;

	/**
	 * Returns the IDs of any batches sent to the given contact that should now
	 * be considered lost.
	 * <p>
	 * Locking: contact read, message read, messageStatus read.
	 */
	Collection<BatchId> getLostBatches(T txn, ContactId c) throws DbException;

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
	 * Locking: message read, messageFlag read.
	 */
	Collection<MessageHeader> getMessageHeaders(T txn, GroupId g)
			throws DbException;

	/**
	 * Returns the message identified by the given ID, in raw format, or null
	 * if the message is not present in the database or is not sendable to the
	 * given contact.
	 * <p>
	 * Locking: contact read, message read, messageStatus read,
	 * subscription read.
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
	 * Returns the number of children of the message identified by the given
	 * ID that are present in the database and have sendability scores greater
	 * than zero.
	 * <p>
	 * Locking: message read.
	 */
	int getNumberOfSendableChildren(T txn, MessageId m) throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be sent to the
	 * given contact, up to the given number of messages.
	 * <p>
	 * Locking: contact read, message read, messageStatus read,
	 * subscription read.
	 */
	Collection<MessageId> getOfferableMessages(T txn, ContactId c,
			int maxMessages) throws DbException;

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
	 * Locking: message read, messageFlag read.
	 */
	boolean getRead(T txn, MessageId m) throws DbException;

	/**
	 * Returns all remote properties for the given transport.
	 * <p>
	 * Locking: contact read, transport read.
	 */
	Map<ContactId, TransportProperties> getRemoteProperties(T txn,
			TransportId t) throws DbException;

	/**
	 * Returns all temporary secrets.
	 * <p>
	 * Locking: contact read, window read.
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
	 * given contact, with a total size less than or equal to the given size.
	 * <p>
	 * Locking: contact read, message read, messageStatus read,
	 * subscription read.
	 */
	Collection<MessageId> getSendableMessages(T txn, ContactId c, int capacity)
			throws DbException;

	/**
	 * Returns true if the given message has been starred.
	 * <p>
	 * Locking: message read, messageFlag read.
	 */
	boolean getStarred(T txn, MessageId m) throws DbException;

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
	 * Returns the time at which the local transports were last modified.
	 * <p>
	 * Locking: transport read.
	 */
	long getTransportsModified(T txn) throws DbException;

	/**
	 * Returns the time at which a transport update was last sent to the given
	 * contact.
	 * <p>
	 * Locking: contact read, transport read.
	 */
	long getTransportsSent(T txn, ContactId c) throws DbException;

	/**
	 * Returns the number of unread messages in each subscribed group.
	 * <p>
	 * Locking: message read, messageFlag read, subscription read.
	 */
	Map<GroupId, Integer> getUnreadMessageCounts(T txn) throws DbException;

	/**
	 * Returns the contacts to which the given group is visible.
	 * <p>
	 * Locking: contact read, subscription read.
	 */
	Collection<ContactId> getVisibility(T txn, GroupId g) throws DbException;

	/**
	 * Returns any holes covering unsubscriptions that are visible to the given
	 * contact, occurred strictly before the given timestamp, and have not yet
	 * been acknowledged.
	 * <p>
	 * Locking: contact read, subscription read.
	 */
	Map<GroupId, GroupId> getVisibleHoles(T txn, ContactId c, long timestamp)
			throws DbException;

	/**
	 * Returns any subscriptions that are visible to the given contact,
	 * occurred strictly before the given timestamp, and have not yet been
	 * acknowledged.
	 * <p>
	 * Locking: contact read, subscription read.
	 */
	Map<Group, Long> getVisibleSubscriptions(T txn, ContactId c, long timestamp)
			throws DbException;

	/**
	 * Returns true if any messages are sendable to the given contact.
	 * <p>
	 * Locking: contact read, message read, messageStatus read.
	 */
	boolean hasSendableMessages(T txn, ContactId c) throws DbException;

	/**
	 * Increments the outgoing connection counter for the given contact
	 * transport in the given rotation period and returns the old value;
	 * <p>
	 * Locking: contact read, window write.
	 */
	long incrementConnectionCounter(T txn, ContactId c, TransportId t,
			long period) throws DbException;

	/**
	 * Removes an outstanding batch that has been acknowledged. Any messages in
	 * the batch that are still considered outstanding (Status.SENT) with
	 * respect to the given contact are now considered seen (Status.SEEN).
	 * <p>
	 * Locking: contact read, message read, messageStatus write.
	 */
	void removeAckedBatch(T txn, ContactId c, BatchId b) throws DbException;

	/**
	 * Marks the given batches received from the given contact as having been
	 * acknowledged.
	 * <p>
	 * Locking: contact read, messageStatus write.
	 */
	void removeBatchesToAck(T txn, ContactId c, Collection<BatchId> sent)
			throws DbException;

	/**
	 * Removes a contact (and all associated state) from the database.
	 * <p>
	 * Locking: contact write, message write, messageFlag write,
	 * messageStatus write, subscription write, transport write, window write.
	 */
	void removeContact(T txn, ContactId c) throws DbException;

	/**
	 * Removes an outstanding batch that has been lost. Any messages in the
	 * batch that are still considered outstanding (Status.SENT) with respect
	 * to the given contact are now considered unsent (Status.NEW).
	 * <p>
	 * Locking: contact read, message read, messageStatus write.
	 */
	void removeLostBatch(T txn, ContactId c, BatchId b) throws DbException;

	/**
	 * Removes a message (and all associated state) from the database.
	 * <p>
	 * Locking: contact read, message write, messageFlag write,
	 * messageStatus write.
	 */
	void removeMessage(T txn, MessageId m) throws DbException;

	/**
	 * Unsubscribes from the given group. Any messages belonging to the group
	 * are deleted from the database.
	 * <p>
	 * Locking: contact read, message write, messageFlag write,
	 * messageStatus write, subscription write.
	 */
	void removeSubscription(T txn, GroupId g) throws DbException;

	/**
	 * Removes any subscriptions for the given contact with IDs between the
	 * given IDs. If both of the given IDs are null, all subscriptions are
	 * removed. If only the first is null, all subscriptions with IDs less than
	 * the second ID are removed. If onlt the second is null, all subscriptions
	 * with IDs greater than the first are removed.
	 */
	void removeSubscriptions(T txn, ContactId c, GroupId start, GroupId end)
			throws DbException;

	/**
	 * Makes the given group invisible to the given contact.
	 * <p>
	 * Locking: contact read, subscription write.
	 */
	void removeVisibility(T txn, ContactId c, GroupId g) throws DbException;

	/**
	 * Sets the configuration for the given transport, replacing any existing
	 * configuration for that transport.
	 * <p>
	 * Locking: transport write.
	 */
	void setConfig(T txn, TransportId t, TransportConfig config)
			throws DbException;

	/**
	 * Sets the connection reordering window for the given contact transport in
	 * the given rotation period.
	 * <p>
	 * Locking: contact read, window write.
	 */
	void setConnectionWindow(T txn, ContactId c, TransportId t, long period,
			long centre, byte[] bitmap) throws DbException;

	/**
	 * Sets the given contact's database expiry time.
	 * <p>
	 * Locking: contact read, subscription write.
	 */
	void setExpiryTime(T txn, ContactId c, long expiry) throws DbException;

	/**
	 * Sets the local transport properties for the given transport, replacing
	 * any existing properties for that transport.
	 * <p>
	 * Locking: transport write.
	 */
	void setLocalProperties(T txn, TransportId t, TransportProperties p)
			throws DbException;

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
	 * Locking: message read, messageFlag write.
	 */
	boolean setRead(T txn, MessageId m, boolean read) throws DbException;

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
	 * Locking: message read, messageFlag write.
	 */
	boolean setStarred(T txn, MessageId m, boolean starred) throws DbException;

	/**
	 * Sets the status of the given message with respect to the given contact.
	 * <p>
	 * Locking: contact read, message read, messageStatus write.
	 */
	void setStatus(T txn, ContactId c, MessageId m, Status s)
			throws DbException;

	/**
	 * If the database contains the given message and it belongs to a group
	 * that is visible to the given contact, sets the status of the message
	 * with respect to the contact to Status.SEEN and returns true; otherwise
	 * returns false.
	 * <p>
	 * Locking: contact read, message read, messageStatus write,
	 * subscription read.
	 */
	boolean setStatusSeenIfVisible(T txn, ContactId c, MessageId m)
			throws DbException;

	/**
	 * Records the time of the latest subscription update acknowledged by the
	 * given contact.
	 * <p>
	 * Locking: contact read, subscription write.
	 */
	void setSubscriptionsAcked(T txn, ContactId c, long timestamp)
			throws DbException;

	/**
	 * Records the time of the latest subscription update received from the
	 * given contact.
	 * <p>
	 * Locking: contact read, subscription write.
	 */
	void setSubscriptionsReceived(T txn, ContactId c, long timestamp)
			throws DbException;

	/**
	 * Sets the transports for the given contact, replacing any existing
	 * transports unless the existing transports have a newer timestamp.
	 * <p>
	 * Locking: contact read, transport write.
	 */
	void setTransports(T txn, ContactId c, Collection<Transport> transports,
			long timestamp) throws DbException;

	/**
	 * Records the time at which the local transports were last modified.
	 * <p>
	 * Locking: contact read, transport write.
	 */
	void setTransportsModified(T txn, long timestamp) throws DbException;

	/**
	 * Records the time at which a transport update was last sent to the given
	 * contact.
	 * <p>
	 * Locking: contact read, transport write.
	 */
	void setTransportsSent(T txn, ContactId c, long timestamp)
			throws DbException;
}
