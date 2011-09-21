package net.sf.briar.db;

import java.util.Collection;
import java.util.Map;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.Status;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.transport.ConnectionWindow;

/**
 * A low-level interface to the database (DatabaseComponent provides a
 * high-level interface). Most operations take a transaction argument, which is
 * obtained by calling startTransaction(). Every transaction must be
 * terminated by calling either abortTransaction() or commitTransaction(),
 * even if an exception is thrown.
 * 
 * Locking is provided by the DatabaseComponent implementation. To prevent
 * deadlock, locks must be acquired in the following order:
 * <ul>
 * <li> contacts
 * <li> messages
 * <li> messageStatuses
 * <li> ratings
 * <li> subscriptions
 * <li> transports
 * <li> windows
 * </ul>
 */
interface Database<T> {

	/**
	 * A batch sent to a contact is considered lost when this many more
	 * recently sent batches have been acknowledged.
	 */
	static final int RETRANSMIT_THRESHOLD = 5;

	/**
	 * Opens the database.
	 * @param resume True to reopen an existing database, false to create a
	 * new one.
	 */
	void open(boolean resume) throws DbException;

	/**
	 * Prevents new transactions from starting, waits for all current
	 * transactions to finish, and closes the database.
	 */
	void close() throws DbException;

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
	 * Locking: contacts read, messageStatuses write.
	 */
	void addBatchToAck(T txn, ContactId c, BatchId b) throws DbException;

	/**
	 * Adds a new contact to the database with the given transport properties
	 * and secret, and returns an ID for the contact.
	 * <p>
	 * Locking: contacts write, transports write.
	 */
	ContactId addContact(T txn, Map<String, Map<String, String>> transports,
			byte[] secret) throws DbException;

	/**
	 * Returns false if the given message is already in the database. Otherwise
	 * stores the message and returns true.
	 * <p>
	 * Locking: messages write.
	 */
	boolean addGroupMessage(T txn, Message m) throws DbException;

	/**
	 * Records a sent batch as needing to be acknowledged.
	 * <p>
	 * Locking: contacts read, messages read, messageStatuses write.
	 */
	void addOutstandingBatch(T txn, ContactId c, BatchId b,
			Collection<MessageId> sent) throws DbException;

	/**
	 * Returns false if the given message is already in the database. Otherwise
	 * stores the message and returns true.
	 * <p>
	 * Locking: contacts read, messages write.
	 */
	boolean addPrivateMessage(T txn, Message m, ContactId c) throws DbException;

	/**
	 * Subscribes to the given group.
	 * <p>
	 * Locking: subscriptions write.
	 */
	void addSubscription(T txn, Group g) throws DbException;

	/**
	 * Returns true if the database contains the given contact.
	 * <p>
	 * Locking: contacts read.
	 */
	boolean containsContact(T txn, ContactId c) throws DbException;

	/**
	 * Returns true if the database contains the given message.
	 * <p>
	 * Locking: messages read.
	 */
	boolean containsMessage(T txn, MessageId m) throws DbException;

	/**
	 * Returns true if the user subscribes to the given group.
	 * <p>
	 * Locking: subscriptions read.
	 */
	boolean containsSubscription(T txn, GroupId g) throws DbException;

	/**
	 * Returns true if the user has been subscribed to the given group since
	 * the given time.
	 * <p>
	 * Locking: subscriptions read.
	 */
	boolean containsSubscription(T txn, GroupId g, long time)
	throws DbException;

	/**
	 * Returns true if the user is subscribed to the given group, the group is
	 * visible to the given contact, and the subscription has existed since the
	 * given time.
	 * <p>
	 * Locking: contacts read, subscriptions read.
	 */
	boolean containsVisibleSubscription(T txn, GroupId g, ContactId c,
			long time) throws DbException;

	/**
	 * Returns the IDs of any batches received from the given contact that need
	 * to be acknowledged.
	 * <p>
	 * Locking: contacts read, messageStatuses read.
	 */
	Collection<BatchId> getBatchesToAck(T txn, ContactId c) throws DbException;

	/**
	 * Returns the connection reordering window for the given contact and
	 * transport.
	 * <p>
	 * Locking: contacts read, windows read.
	 */
	ConnectionWindow getConnectionWindow(T txn, ContactId c, int transportId)
	throws DbException;

	/**
	 * Returns the IDs of all contacts.
	 * <p>
	 * Locking: contacts read.
	 */
	Collection<ContactId> getContacts(T txn) throws DbException;

	/**
	 * Returns the amount of free storage space available to the database, in
	 * bytes. This is based on the minimum of the space available on the device
	 * where the database is stored and the database's configured size.
	 * <p>
	 * Locking: messages read.
	 */
	long getFreeSpace() throws DbException;

	/**
	 * Returns the group that contains the given message.
	 * <p>
	 * Locking: messages read.
	 */
	GroupId getGroup(T txn, MessageId m) throws DbException;

	/**
	 * Returns the IDs of any batches sent to the given contact that should now
	 * be considered lost.
	 * <p>
	 * Locking: contacts read, messages read, messageStatuses read.
	 */
	Collection<BatchId> getLostBatches(T txn, ContactId c) throws DbException;

	/**
	 * Returns the message identified by the given ID, in raw format.
	 * <p>
	 * Locking: messages read.
	 */
	byte[] getMessage(T txn, MessageId m) throws DbException;

	/**
	 * Returns the message identified by the given ID, in raw format, or null
	 * if the message is not present in the database or is not sendable to the
	 * given contact.
	 * <p>
	 * Locking: contacts read, messages read, messageStatuses read,
	 * subscriptions read.
	 */
	byte[] getMessageIfSendable(T txn, ContactId c, MessageId m)
	throws DbException;

	/**
	 * Returns the IDs of all messages signed by the given author.
	 * <p>
	 * Locking: messages read.
	 */
	Collection<MessageId> getMessagesByAuthor(T txn, AuthorId a)
	throws DbException;

	/**
	 * Returns the number of children of the message identified by the given
	 * ID that are present in the database and have sendability scores greater
	 * than zero.
	 * <p>
	 * Locking: messages read.
	 */
	int getNumberOfSendableChildren(T txn, MessageId m) throws DbException;

	/**
	 * Returns the IDs of the oldest messages in the database, with a total
	 * size less than or equal to the given size.
	 * <p>
	 * Locking: messages read.
	 */
	Collection<MessageId> getOldMessages(T txn, int size) throws DbException;

	/**
	 * Returns the parent of the given message, or null if the message has
	 * no parent.
	 * <p>
	 * Locking: messages read.
	 */
	MessageId getParent(T txn, MessageId m) throws DbException;

	/**
	 * Returns the user's rating for the given author.
	 * <p>
	 * Locking: ratings read.
	 */
	Rating getRating(T txn, AuthorId a) throws DbException;

	/**
	 * Returns the sendability score of the given group message.
	 * <p>
	 * Locking: messages read.
	 */
	int getSendability(T txn, MessageId m) throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be sent to the
	 * given contact.
	 * <p>
	 * Locking: contacts read, messages read, messageStatuses read,
	 * subscriptions read.
	 */
	Collection<MessageId> getSendableMessages(T txn, ContactId c)
	throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be sent to the
	 * given contact, with a total size less than or equal to the given size.
	 * <p>
	 * Locking: contacts read, messages read, messageStatuses read,
	 * subscriptions read.
	 */
	Collection<MessageId> getSendableMessages(T txn, ContactId c, int size)
	throws DbException;

	/**
	 * Returns the secret shared with the given contact.
	 * <p>
	 * Locking: contacts read.
	 */
	byte[] getSharedSecret(T txn, ContactId c) throws DbException;

	/**
	 * Returns the groups to which the user subscribes.
	 * <p>
	 * Locking: subscriptions read.
	 */
	Collection<Group> getSubscriptions(T txn) throws DbException;

	/**
	 * Returns the groups to which the given contact subscribes.
	 * <p>
	 * Locking: contacts read, subscriptions read.
	 */
	Collection<Group> getSubscriptions(T txn, ContactId c) throws DbException;

	/**
	 * Returns the configuration for the transport with the given name.
	 * <p>
	 * Locking: transports read.
	 */
	Map<String, String> getTransportConfig(T txn, String name)
	throws DbException;

	/**
	 * Returns all local transport properties.
	 * <p>
	 * Locking: transports read.
	 */
	Map<String, Map<String, String>> getTransports(T txn) throws DbException;

	/**
	 * Returns all transport properties for the given contact.
	 * <p>
	 * Locking: contacts read, transports read.
	 */
	Map<String, Map<String, String>> getTransports(T txn, ContactId c)
	throws DbException;

	/**
	 * Returns the contacts to which the given group is visible.
	 * <p>
	 * Locking: contacts read, subscriptions read.
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
	 * Locking: contacts read, messages read, messageStatuses read.
	 */
	boolean hasSendableMessages(T txn, ContactId c) throws DbException;

	/**
	 * Removes an outstanding batch that has been acknowledged. Any messages in
	 * the batch that are still considered outstanding (Status.SENT) with
	 * respect to the given contact are now considered seen (Status.SEEN).
	 * <p>
	 * Locking: contacts read, messages read, messageStatuses write.
	 */
	void removeAckedBatch(T txn, ContactId c, BatchId b) throws DbException;

	/**
	 * Marks the given batches received from the given contact as having been
	 * acknowledged.
	 * <p>
	 * Locking: contacts read, messageStatuses write.
	 */
	void removeBatchesToAck(T txn, ContactId c, Collection<BatchId> sent)
	throws DbException;

	/**
	 * Removes a contact (and all associated state) from the database.
	 * <p>
	 * Locking: contacts write, messages write, messageStatuses write,
	 * subscriptions write, transports write.
	 */
	void removeContact(T txn, ContactId c) throws DbException;

	/**
	 * Removes an outstanding batch that has been lost. Any messages in the
	 * batch that are still considered outstanding (Status.SENT) with respect
	 * to the given contact are now considered unsent (Status.NEW).
	 * <p>
	 * Locking: contacts read, messages read, messageStatuses write.
	 */
	void removeLostBatch(T txn, ContactId c, BatchId b) throws DbException;

	/**
	 * Removes a message (and all associated state) from the database.
	 * <p>
	 * Locking: contacts read, messages write, messageStatuses write.
	 */
	void removeMessage(T txn, MessageId m) throws DbException;

	/**
	 * Unsubscribes from the given group. Any messages belonging to the group
	 * are deleted from the database.
	 * <p>
	 * Locking: contacts read, messages write, messageStatuses write,
	 * subscriptions write.
	 */
	void removeSubscription(T txn, GroupId g) throws DbException;

	/**
	 * Sets the connection reordering window for the given contact and
	 * transport.
	 * <p>
	 * Locking: contacts read, windows write.
	 */
	void setConnectionWindow(T txn, ContactId c, int transportId,
			ConnectionWindow w) throws DbException;

	/**
	 * Sets the user's rating for the given author.
	 * <p>
	 * Locking: ratings write.
	 */
	Rating setRating(T txn, AuthorId a, Rating r) throws DbException;

	/**
	 * Sets the sendability score of the given message.
	 * <p>
	 * Locking: messages write.
	 */
	void setSendability(T txn, MessageId m, int sendability) throws DbException;

	/**
	 * Sets the status of the given message with respect to the given contact.
	 * <p>
	 * Locking: contacts read, messages read, messageStatuses write.
	 */
	void setStatus(T txn, ContactId c, MessageId m, Status s)
	throws DbException;

	/**
	 * If the database contains the given message and it belongs to a group
	 * that is visible to the given contact, sets the status of the message
	 * with respect to the contact to Status.SEEN and returns true; otherwise
	 * returns false.
	 * <p>
	 * Locking: contacts read, messages read, messageStatuses write,
	 * subscriptions read.
	 */
	boolean setStatusSeenIfVisible(T txn, ContactId c, MessageId m)
	throws DbException;

	/**
	 * Sets the subscriptions for the given contact, replacing any existing
	 * subscriptions unless the existing subscriptions have a newer timestamp.
	 * <p>
	 * Locking: contacts read, subscriptions write.
	 */
	void setSubscriptions(T txn, ContactId c, Map<Group, Long> subs,
			long timestamp) throws DbException;

	/**
	 * Sets the configuration for the transport with the given name, replacing
	 * any existing configuration for that transport.
	 * <p>
	 * Locking: transports write.
	 */
	void setTransportConfig(T txn, String name, Map<String, String> config)
	throws DbException;

	/**
	 * Sets the transport properties for the transport with the given name,
	 * replacing any existing properties for that transport.
	 * <p>
	 * Locking: transports write.
	 */
	void setTransportProperties(T txn, String name,
			Map<String, String> properties) throws DbException;

	/**
	 * Sets the transport properties for the given contact, replacing any
	 * existing properties unless the existing properties have a newer
	 * timestamp.
	 * <p>
	 * Locking: contacts read, transports write.
	 */
	void setTransports(T txn, ContactId c,
			Map<String, Map<String, String>> transports, long timestamp)
	throws DbException;

	/**
	 * Makes the given group visible to the given set of contacts and invisible
	 * to any other contacts.
	 * <p>
	 * Locking: contacts read, subscriptions write.
	 */
	void setVisibility(T txn, GroupId g, Collection<ContactId> visible)
	throws DbException;
}
