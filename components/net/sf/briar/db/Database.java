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

interface Database<T> {

	void open(boolean resume) throws DbException;

	void close() throws DbException;

	T startTransaction(String name) throws DbException;

	void abortTransaction(T txn);

	void commitTransaction(T txn) throws DbException;

	// Locking: neighbours write
	void addBatchToAck(T txn, NeighbourId n, BatchId b) throws DbException;

	// Locking: neighbours write
	void addNeighbour(T txn, NeighbourId n) throws DbException;

	// Locking: neighbours write, messages read
	void addOutstandingBatch(T txn, NeighbourId n, BatchId b, Set<MessageId> sent) throws DbException;

	// Locking: neighbours write, messages read
	Set<BatchId> addReceivedBundle(T txn, NeighbourId n, BundleId b) throws DbException;

	// Locking: subscriptions write
	void addSubscription(T txn, GroupId g) throws DbException;

	// Locking: neighbours write
	void addSubscription(T txn, NeighbourId n, GroupId g) throws DbException;

	// Locking: neighbours write
	void clearSubscriptions(T txn, NeighbourId n) throws DbException;

	// Locking: messages read
	boolean containsMessage(T txn, MessageId m) throws DbException;

	// Locking: subscriptions read
	boolean containsSubscription(T txn, GroupId g) throws DbException;

	// Locking: messages read
	long getFreeSpace() throws DbException;

	// Locking: messages read
	Message getMessage(T txn, MessageId m) throws DbException;

	// Locking: messages read
	Iterable<MessageId> getMessagesByAuthor(T txn, AuthorId a) throws DbException;

	// Locking: messages read
	Iterable<MessageId> getMessagesByParent(T txn, MessageId m) throws DbException;

	// Locking: neighbours read
	Set<NeighbourId> getNeighbours(T txn) throws DbException;

	// Locking: messages read
	Iterable<MessageId> getOldMessages(T txn, long size) throws DbException;

	// Locking: messages read
	MessageId getParent(T txn, MessageId m) throws DbException;

	// Locking: ratings read
	Rating getRating(T txn, AuthorId a) throws DbException;

	// Locking: messages read
	int getSendability(T txn, MessageId m) throws DbException;

	// Locking: neighbours read, messages read
	Iterable<MessageId> getSendableMessages(T txn, NeighbourId n, long capacity) throws DbException;

	// Locking: subscriptions read
	Set<GroupId> getSubscriptions(T txn) throws DbException;

	// Locking: messages write
	boolean addMessage(T txn, Message m) throws DbException;

	// Locking: ratings write
	Rating setRating(T txn, AuthorId a, Rating r) throws DbException;

	// Locking: messages write
	void setSendability(T txn, MessageId m, int sendability) throws DbException;

	// Locking: neighbours read, n write
	Set<BatchId> removeBatchesToAck(T txn, NeighbourId n) throws DbException;

	// Locking: neighbours write, messages read
	void removeLostBatch(T txn, NeighbourId n, BatchId b) throws DbException;

	// Locking: neighbours write, messages write
	void removeMessage(T txn, MessageId m) throws DbException;

	// Locking: neighbours write
	Set<MessageId> removeOutstandingBatch(T txn, NeighbourId n, BatchId b) throws DbException;

	// Locking: subscriptions write, neighbours write, messages write
	void removeSubscription(T txn, GroupId g) throws DbException;

	// Locking: neighbours write, messages read
	void setStatus(T txn, NeighbourId n, MessageId m, Status s) throws DbException;
}
