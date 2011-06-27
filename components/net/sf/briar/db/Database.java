package net.sf.briar.db;

import java.util.Set;

import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NeighbourId;
import net.sf.briar.api.db.Rating;
import net.sf.briar.api.db.Status;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BundleId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;

/**
 * A low-level interface to the database that is managed by a
 * DatabaseComponent. Most operations take a transaction argument, which is
 * obtained by calling startTransaction(). Every transaction must be
 * terminated by calling either abortTransaction() or commitTransaction(),
 * even if an exception is thrown.
 */
interface Database<T> {

	/**
	 * Opens the database.
	 * @param resume True to reopen an existing database, false to create a
	 * new one.
	 */
	void open(boolean resume) throws DbException;

	/** Waits for all open transactions to finish and closes the database. */
	void close() throws DbException;

	/** Starts a new transaction and returns an object representing it. */
	T startTransaction(String name) throws DbException;

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
	 * Locking: neighbours write.
	 */
	void addBatchToAck(T txn, NeighbourId n, BatchId b) throws DbException;

	/**
	 * Returns false if the given message is already in the database. Otherwise
	 * stores the message and returns true.
	 * Locking: messages write.
	 */
	boolean addMessage(T txn, Message m) throws DbException;

	/**
	 * Adds a new neighbour to the database.
	 * Locking: neighbours write.
	 */
	void addNeighbour(T txn, NeighbourId n) throws DbException;

	/**
	 * Records a sent batch as needing to be acknowledged.
	 * Locking: neighbours write, messages read.
	 */
	void addOutstandingBatch(T txn, NeighbourId n, BatchId b, Set<MessageId> sent) throws DbException;

	/**
	 * Records a received bundle. This should be called after processing the
	 * bundle's contents, and may result in outstanding messages becoming
	 * eligible for retransmittion.
	 * Locking: neighbours write, messages read.
	 */
	Set<BatchId> addReceivedBundle(T txn, NeighbourId n, BundleId b) throws DbException;

	/**
	 * Subscribes to the given group.
	 * Locking: subscriptions write.
	 */
	void addSubscription(T txn, GroupId g) throws DbException;

	/**
	 * Records a neighbour's subscription to a group.
	 * Locking: neighbours write.
	 */
	void addSubscription(T txn, NeighbourId n, GroupId g) throws DbException;

	/**
	 * Removes all recorded subscriptions for the given neighbour.
	 * Locking: neighbours write.
	 */
	void clearSubscriptions(T txn, NeighbourId n) throws DbException;

	/**
	 * Returns true iff the database contains the given message.
	 * Locking: messages read.
	 */
	boolean containsMessage(T txn, MessageId m) throws DbException;

	/**
	 * Returns true iff the user is subscribed to the given group.
	 * Locking: subscriptions read.
	 */
	boolean containsSubscription(T txn, GroupId g) throws DbException;

	/**
	 * Returns the amount of free storage space available to the database, in
	 * bytes. This is based on the minimum of the space available on the device
	 * where the database is stored and the database's configured size.
	 * Locking: messages read.
	 */
	long getFreeSpace() throws DbException;

	/**
	 * Returns the message identified by the given ID.
	 * Locking: messages read.
	 */
	Message getMessage(T txn, MessageId m) throws DbException;

	/**
	 * Returns the IDs of all messages signed by the given author.
	 * Locking: messages read.
	 */
	Iterable<MessageId> getMessagesByAuthor(T txn, AuthorId a) throws DbException;

	/**
	 * Returns the IDs of all children of the message identified by the given
	 * ID that are present in the database.
	 * Locking: messages read.
	 */
	Iterable<MessageId> getMessagesByParent(T txn, MessageId m) throws DbException;

	/**
	 * Returns the IDs of all neighbours
	 * Locking: neighbours read.
	 */
	Set<NeighbourId> getNeighbours(T txn) throws DbException;

	/**
	 * Returns the IDs of the oldest messages in the database, with a total
	 * size less than or equal to the given size.
	 * Locking: messages read.
	 */
	Iterable<MessageId> getOldMessages(T txn, long size) throws DbException;

	/**
	 * Returns the parent of the given message.
	 * Locking: messages read.
	 */
	MessageId getParent(T txn, MessageId m) throws DbException;

	/**
	 * Returns the user's rating for the given author.
	 * Locking: ratings read.
	 */
	Rating getRating(T txn, AuthorId a) throws DbException;

	/**
	 * Returns the sendability score of the given message. Messages with
	 * sendability scores greater than zero are eligible to be sent to
	 * neighbours.
	 * Locking: messages read.
	 */
	int getSendability(T txn, MessageId m) throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be sent to the
	 * given neighbour, with a total size less than or equal to the given size.
	 * Locking: neighbours read, messages read.
	 */
	Iterable<MessageId> getSendableMessages(T txn, NeighbourId n, long capacity) throws DbException;

	/**
	 * Returns the groups to which the user subscribes.
	 * Locking: subscriptions read.
	 */
	Set<GroupId> getSubscriptions(T txn) throws DbException;

	/**
	 * Removes and returns the IDs of any batches received from the given
	 * neighbour that need to be acknowledged.
	 * Locking: neighbours write.
	 */
	Set<BatchId> removeBatchesToAck(T txn, NeighbourId n) throws DbException;

	/**
	 * Removes an outstanding batch that is considered to have been lost. Any
	 * messages in the batch that are still considered outstanding
	 * (Status.SENT) with respect to the given neighbour are now considered
	 * unsent (Status.NEW).
	 * Locking: neighbours write, messages read.
	 */
	void removeLostBatch(T txn, NeighbourId n, BatchId b) throws DbException;

	/**
	 * Removes a message from the database.
	 * Locking: neighbours write, messages write.
	 */
	void removeMessage(T txn, MessageId m) throws DbException;

	/**
	 * Removes an outstanding batch that has been acknowledged. The status of
	 * the acknowledged messages is not updated.
	 * Locking: neighbours write.
	 */
	Set<MessageId> removeOutstandingBatch(T txn, NeighbourId n, BatchId b) throws DbException;

	/**
	 * Unsubscribes from the given group. Any messages belonging to the group
	 * are deleted from the database.
	 * Locking: subscriptions write, neighbours write, messages write.
	 */
	void removeSubscription(T txn, GroupId g) throws DbException;

	/**
	 * Records the user's rating for the given author.
	 * Locking: ratings write.
	 */
	Rating setRating(T txn, AuthorId a, Rating r) throws DbException;

	/**
	 * Records the sendability score of the given message.
	 * Locking: messages write.
	 */
	void setSendability(T txn, MessageId m, int sendability) throws DbException;

	/**
	 * Sets the status of the given message with respect to the given neighbour. 
	 * Locking: neighbours write, messages read
	 */
	void setStatus(T txn, NeighbourId n, MessageId m, Status s) throws DbException;
}
