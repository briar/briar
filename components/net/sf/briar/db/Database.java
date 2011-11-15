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
import net.sf.briar.api.db.Status;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionWindow;

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
	 * Adds a new contact to the database with the given secrets and returns an
	 * ID for the contact.
	 * <p>
	 * Locking: contact write.
	 */
	ContactId addContact(T txn, byte[] incomingSecret, byte[] outgoingSecret)
	throws DbException;

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
	 * Subscribes to the given group.
	 * <p>
	 * Locking: subscription write.
	 */
	void addSubscription(T txn, Group g) throws DbException;

	/**
	 * Allocates and returns a local index for the given transport. Returns
	 * null if all indices have been allocated.
	 * <p>
	 * Locking: transport write.
	 */
	TransportIndex addTransport(T txn, TransportId t) throws DbException;

	/**
	 * Returns true if the database contains the given contact.
	 * <p>
	 * Locking: contact read.
	 */
	boolean containsContact(T txn, ContactId c) throws DbException;

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
	Collection<BatchId> getBatchesToAck(T txn, ContactId c) throws DbException;

	/**
	 * Returns the configuration for the given transport.
	 * <p>
	 * Locking: transport read.
	 */
	TransportConfig getConfig(T txn, TransportId t) throws DbException;

	/**
	 * Returns an outgoing connection context for the given contact and
	 * transport.
	 * <p>
	 * Locking: contact read, window write.
	 */
	ConnectionContext getConnectionContext(T txn, ContactId c, TransportIndex i)
	throws DbException;

	/**
	 * Returns the connection reordering window for the given contact and
	 * transport.
	 * <p>
	 * Locking: contact read, window read.
	 */
	ConnectionWindow getConnectionWindow(T txn, ContactId c, TransportIndex i)
	throws DbException;

	/**
	 * Returns the IDs of all contacts.
	 * <p>
	 * Locking: contact read.
	 */
	Collection<ContactId> getContacts(T txn) throws DbException;

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
	 * Returns the local index for the given transport, or null if no index
	 * has been allocated.
	 * <p>
	 * Locking: transport read.
	 */
	TransportIndex getLocalIndex(T txn, TransportId t) throws DbException;

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
	 * Returns the given contact's index for the given transport, or null if
	 * the contact does not support the transport.
	 * <p>
	 * Locking: contact read, window read.
	 */
	TransportIndex getRemoteIndex(T txn, ContactId c, TransportId t)
	throws DbException;

	/**
	 * Returns all remote properties for the given transport.
	 * <p>
	 * Locking: contact read, transport read.
	 */
	Map<ContactId, TransportProperties> getRemoteProperties(T txn,
			TransportId t) throws DbException;

	/**
	 * Returns the sendability score of the given group message.
	 * <p>
	 * Locking: message read.
	 */
	int getSendability(T txn, MessageId m) throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be sent to the
	 * given contact.
	 * <p>
	 * Locking: contact read, message read, messageStatus read,
	 * subscription read.
	 */
	Collection<MessageId> getSendableMessages(T txn, ContactId c)
	throws DbException;

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
	 * Returns the secret shared with the given contact.
	 * <p>
	 * Locking: contact read.
	 */
	byte[] getSharedSecret(T txn, ContactId c, boolean incoming)
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
	 * Returns the time at which the subscriptions visible to the given contact
	 * were last modified.
	 * <p>
	 * Locking: contact read, subscription read.
	 */
	long getSubscriptionsModified(T txn, ContactId c) throws DbException;

	/**
	 * Returns the time at which a subscription update was last sent to the
	 * given contact.
	 * <p>
	 * Locking: contact read, subscription read.
	 */
	long getSubscriptionsSent(T txn, ContactId c) throws DbException;

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
	 * Returns the groups to which the user subscribes that are visible to the
	 * given contact.
	 */
	Map<Group, Long> getVisibleSubscriptions(T txn, ContactId c)
	throws DbException;

	/**
	 * Returns true if any messages are sendable to the given contact.
	 * <p>
	 * Locking: contact read, message read, messageStatus read.
	 */
	boolean hasSendableMessages(T txn, ContactId c) throws DbException;

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
	 * Sets the configuration for the given transport, replacing any existing
	 * configuration for that transport.
	 * <p>
	 * Locking: transport write.
	 */
	void setConfig(T txn, TransportId t, TransportConfig config)
	throws DbException;

	/**
	 * Sets the connection reordering window for the given contact and
	 * transport.
	 * <p>
	 * Locking: contact read, window write.
	 */
	void setConnectionWindow(T txn, ContactId c, TransportIndex i,
			ConnectionWindow w) throws DbException;

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
	 * Sets the subscriptions for the given contact, replacing any existing
	 * subscriptions unless the existing subscriptions have a newer timestamp.
	 * <p>
	 * Locking: contact read, subscription write.
	 */
	void setSubscriptions(T txn, ContactId c, Map<Group, Long> subs,
			long timestamp) throws DbException;

	/**
	 * Records the time at which the subscriptions visible to the given contacts
	 * were last modified.
	 * <p>
	 * Locking: contact read, subscription write.
	 */
	void setSubscriptionsModified(T txn, Collection<ContactId> contacts,
			long timestamp) throws DbException;

	/**
	 * Records the time at which a subscription update was last sent to the
	 * given contact.
	 * <p>
	 * Locking: contact read, subscription write.
	 */
	void setSubscriptionsSent(T txn, ContactId c, long timestamp)
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

	/**
	 * Makes the given group visible to the given set of contacts and invisible
	 * to any other contacts.
	 * <p>
	 * Locking: contact read, subscription write.
	 */
	void setVisibility(T txn, GroupId g, Collection<ContactId> visible)
	throws DbException;
}
