package net.sf.briar.db;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import net.sf.briar.api.Author;
import net.sf.briar.api.AuthorId;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.GroupMessageHeader;
import net.sf.briar.api.db.PrivateMessageHeader;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.LocalGroup;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.messaging.Rating;
import net.sf.briar.api.messaging.RetentionAck;
import net.sf.briar.api.messaging.RetentionUpdate;
import net.sf.briar.api.messaging.SubscriptionAck;
import net.sf.briar.api.messaging.SubscriptionUpdate;
import net.sf.briar.api.messaging.TransportAck;
import net.sf.briar.api.messaging.TransportUpdate;
import net.sf.briar.api.transport.Endpoint;
import net.sf.briar.api.transport.TemporarySecret;

// FIXME: Document the preconditions for calling each method

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
 * <li> identity
 * <li> message
 * <li> rating
 * <li> retention
 * <li> subscription
 * <li> transport
 * <li> window
 * </ul>
 */
interface Database<T> {

	/** Opens the database and returns true if the database already existed. */
	boolean open() throws DbException, IOException;

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
	 * Stores a contact with the given pseudonym, associated with the given
	 * local pseudonym, and returns an ID for the contact.
	 * <p>
	 * Locking: contact write, retention write, subscription write, transport
	 * write, window write.
	 */
	ContactId addContact(T txn, Author remote, AuthorId local)
			throws DbException;

	/**
	 * Stores an endpoint.
	 * <p>
	 * Locking: window write.
	 */
	void addEndpoint(T txn, Endpoint ep) throws DbException;

	/**
	 * Stores the given message, or returns false if the message is already in
	 * the database.
	 * <p>
	 * Locking: message write.
	 */
	boolean addGroupMessage(T txn, Message m) throws DbException;

	/**
	 * Stores a pseudonym that the user can use to sign messages.
	 * <p>
	 * Locking: contact write, identity write.
	 */
	void addLocalAuthor(T txn, LocalAuthor a) throws DbException;

	/**
	 * Stores a restricted group to which the user can post messages. Storing
	 * a group does not create a subscription to it.
	 * <p>
	 * Locking: identity write.
	 */
	void addLocalGroup(T txn, LocalGroup g) throws DbException;

	/**
	 * Records a received message as needing to be acknowledged.
	 * <p>
	 * Locking: message write.
	 */
	void addMessageToAck(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Stores the given message, or returns false if the message is already in
	 * the database.
	 * <p>
	 * Locking: message write.
	 */
	boolean addPrivateMessage(T txn, Message m, ContactId c) throws DbException;

	/**
	 * Stores the given temporary secrets and deletes any secrets that have
	 * been made obsolete.
	 * <p>
	 * Locking: window write.
	 */
	void addSecrets(T txn, Collection<TemporarySecret> secrets)
			throws DbException;

	/**
	 * Initialises the status (seen or unseen) of the given message with
	 * respect to the given contact.
	 * <p>
	 * Locking: message write.
	 */
	void addStatus(T txn, ContactId c, MessageId m, boolean seen)
			throws DbException;

	/**
	 * Subscribes to the given group, or returns false if the user already has
	 * the maximum number of subscriptions.
	 * <p>
	 * Locking: subscription write.
	 */
	boolean addSubscription(T txn, Group g) throws DbException;

	/**
	 * Stores a transport and returns true if the transport was not previously
	 * in the database.
	 * <p>
	 * Locking: transport write, window write.
	 */
	boolean addTransport(T txn, TransportId t, long maxLatency)
			throws DbException;

	/**
	 * Makes the given group visible to the given contact.
	 * <p>
	 * Locking: subscription write.
	 */
	void addVisibility(T txn, ContactId c, GroupId g) throws DbException;

	/**
	 * Returns true if the database contains the given contact.
	 * <p>
	 * Locking: contact read.
	 */
	boolean containsContact(T txn, AuthorId a) throws DbException;

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
	 * Returns true if the database contains the given transport.
	 * <p>
	 * Locking: transport read.
	 */
	boolean containsTransport(T txn, TransportId t) throws DbException;

	/**
	 * Returns true if the user subscribes to the given group and the
	 * subscription is visible to the given contact.
	 * <p>
	 * Locking: subscription read.
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
	 * Returns the contact with the given ID.
	 * <p>
	 * Locking: contact read, window read.
	 */
	Contact getContact(T txn, ContactId c) throws DbException;

	/**
	 * Returns the IDs of all contacts.
	 * <p>
	 * Locking: contact read.
	 */
	Collection<ContactId> getContactIds(T txn) throws DbException;

	/**
	 * Returns all contacts.
	 * <p>
	 * Locking: contact read, window read.
	 */
	Collection<Contact> getContacts(T txn) throws DbException;

	/**
	 * Returns all endpoints.
	 * <p>
	 * Locking: window read.
	 */
	Collection<Endpoint> getEndpoints(T txn) throws DbException;

	/**
	 * Returns the amount of free storage space available to the database, in
	 * bytes. This is based on the minimum of the space available on the device
	 * where the database is stored and the database's configured size.
	 */
	long getFreeSpace() throws DbException;

	/**
	 * Returns the group with the given ID, if the user subscribes to it.
	 */
	Group getGroup(T txn, GroupId g) throws DbException;

	/**
	 * Returns the parent of the given group message, or null if either the
	 * message has no parent, or the parent is absent from the database, or the
	 * parent belongs to a different group.
	 * <p>
	 * Locking: message read.
	 */
	MessageId getGroupMessageParent(T txn, MessageId m) throws DbException;

	/**
	 * Returns the time at which a connection to the given contact was last
	 * made.
	 * <p>
	 * Locking: window read.
	 */
	long getLastConnected(T txn, ContactId c) throws DbException;

	/**
	 * Returns the pseudonym with the given ID.
	 * <p>
	 * Locking: identitiy read.
	 */
	LocalAuthor getLocalAuthor(T txn, AuthorId a) throws DbException;

	/**
	 * Returns all pseudonyms that the user can use to sign messages.
	 * <p>
	 * Locking: identity read.
	 */
	Collection<LocalAuthor> getLocalAuthors(T txn) throws DbException;

	/**
	 * Returns all restricted groups to which the user can post messages.
	 * <p>
	 * Locking: identity read.
	 */
	Collection<LocalGroup> getLocalGroups(T txn) throws DbException;

	/**
	 * Returns the local transport properties for all transports.
	 * <p>
	 * Locking: transport read.
	 */
	Map<TransportId, TransportProperties> getLocalProperties(T txn)
			throws DbException;

	/**
	 * Returns the local transport properties for the given transport.
	 * <p>
	 * Locking: transport read.
	 */
	TransportProperties getLocalProperties(T txn, TransportId t)
			throws DbException;

	/**
	 * Returns the body of the message identified by the given ID.
	 * <p>
	 * Locking: message read.
	 */
	byte[] getMessageBody(T txn, MessageId m) throws DbException;

	/**
	 * Returns the headers of all messages in the given group.
	 * <p>
	 * Locking: message read, rating read.
	 */
	Collection<GroupMessageHeader> getMessageHeaders(T txn, GroupId g)
			throws DbException;

	/**
	 * Returns the headers of all private messages.
	 * <p>
	 * Locking: message read.
	 */
	Collection<PrivateMessageHeader> getPrivateMessageHeaders(T txn)
			throws DbException;

	/**
	 * Returns the headers of all private messages to or from the given
	 * contact.
	 * <p>
	 * Locking: message read.
	 */
	Collection<PrivateMessageHeader> getPrivateMessageHeaders(T txn,
			ContactId c) throws DbException;

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
	 * Locking: message read.
	 */
	Collection<MessageId> getMessagesToAck(T txn, ContactId c, int maxMessages)
			throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be sent to the
	 * given contact, up to the given number of messages.
	 * <p>
	 * Locking: message read, subscription read.
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
	 * Returns the message identified by the given ID, in serialised form.
	 * <p>
	 * Locking: message read.
	 */
	byte[] getRawMessage(T txn, MessageId m) throws DbException;

	/**
	 * Returns the message identified by the given ID, in serialised form.
	 * Returns null if the message is not present in the database or is not
	 * sendable to the given contact.
	 * <p>
	 * Locking: message read, subscription read.
	 */
	byte[] getRawMessageIfSendable(T txn, ContactId c, MessageId m)
			throws DbException;

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
	 * Locking: transport read.
	 */
	Map<ContactId, TransportProperties> getRemoteProperties(T txn,
			TransportId t) throws DbException;

	/**
	 * Returns a retention ack for the given contact, or null if no ack is due.
	 * <p>
	 * Locking: retention write.
	 */
	RetentionAck getRetentionAck(T txn, ContactId c) throws DbException;

	/**
	 * Returns a retention update for the given contact and updates its expiry
	 * time using the given latency. Returns null if no update is due.
	 * <p>
	 * Locking: message read, retention write.
	 */
	RetentionUpdate getRetentionUpdate(T txn, ContactId c, long maxLatency)
			throws DbException;

	/**
	 * Returns all temporary secrets.
	 * <p>
	 * Locking: window read.
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
	 * Locking: message read, subscription read.
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
	 * Locking: subscription read.
	 */
	Collection<Group> getSubscriptions(T txn, ContactId c) throws DbException;

	/**
	 * Returns a subscription ack for the given contact, or null if no ack is
	 * due.
	 * <p>
	 * Locking: subscription write.
	 */
	SubscriptionAck getSubscriptionAck(T txn, ContactId c) throws DbException;

	/**
	 * Returns a subscription update for the given contact and updates its
	 * expiry time using the given latency. Returns null if no update is due.
	 * <p>
	 * Locking: subscription write.
	 */
	SubscriptionUpdate getSubscriptionUpdate(T txn, ContactId c,
			long maxLatency) throws DbException;

	/**
	 * Returns the transmission count of the given message with respect to the
	 * given contact.
	 * <p>
	 * Locking: message read.
	 */
	int getTransmissionCount(T txn, ContactId c, MessageId m)
			throws DbException;

	/**
	 * Returns a collection of transport acks for the given contact, or null if
	 * no acks are due.
	 * <p>
	 * Locking: transport write.
	 */
	Collection<TransportAck> getTransportAcks(T txn, ContactId c)
			throws DbException;

	/**
	 * Returns the maximum latencies of all local transports.
	 * <p>
	 * Locking: transport read.
	 */
	Map<TransportId, Long> getTransportLatencies(T txn) throws DbException;

	/**
	 * Returns a collection of transport updates for the given contact and
	 * updates their expiry times using the given latency. Returns null if no
	 * updates are due.
	 * <p>
	 * Locking: transport write.
	 */
	Collection<TransportUpdate> getTransportUpdates(T txn, ContactId c,
			long maxLatency) throws DbException;

	/**
	 * Returns the number of unread messages in each subscribed group.
	 * <p>
	 * Locking: message read.
	 */
	Map<GroupId, Integer> getUnreadMessageCounts(T txn) throws DbException;

	/**
	 * Returns the contacts to which the given group is visible.
	 * <p>
	 * Locking: subscription read.
	 */
	Collection<ContactId> getVisibility(T txn, GroupId g) throws DbException;

	/**
	 * Returns the subscriptions that are visible to the given contact.
	 * <p>
	 * Locking: subscription read.
	 */
	Collection<GroupId> getVisibleSubscriptions(T txn, ContactId c)
			throws DbException;

	/**
	 * Returns true if any messages are sendable to the given contact.
	 * <p>
	 * Locking: message read, subscription read.
	 */
	boolean hasSendableMessages(T txn, ContactId c) throws DbException;

	/**
	 * Increments the outgoing connection counter for the given endpoint
	 * in the given rotation period and returns the old value, or -1 if the
	 * counter does not exist.
	 * <p>
	 * Locking: window write.
	 */
	long incrementConnectionCounter(T txn, ContactId c, TransportId t,
			long period) throws DbException;

	/**
	 * Increments the retention time versions for all contacts to indicate that
	 * the database's retention time has changed and updates should be sent.
	 * <p>
	 * Locking: retention write.
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
	 * Locking: contact write, message write, retention write,
	 * subscription write, transport write, window write.
	 */
	void removeContact(T txn, ContactId c) throws DbException;

	/**
	 * Removes a message (and all associated state) from the database.
	 * <p>
	 * Locking: message write.
	 */
	void removeMessage(T txn, MessageId m) throws DbException;

	/**
	 * Marks the given messages received from the given contact as having been
	 * acknowledged.
	 * <p>
	 * Locking: message write.
	 */
	void removeMessagesToAck(T txn, ContactId c, Collection<MessageId> acked)
			throws DbException;

	/**
	 * Marks any of the given messages that are considered outstanding with
	 * respect to the given contact as seen by the contact.
	 * <p>
	 * Locking: message write.
	 */
	void removeOutstandingMessages(T txn, ContactId c,
			Collection<MessageId> acked) throws DbException;

	/**
	 * Unsubscribes from the given group. Any messages belonging to the group
	 * are deleted from the database.
	 * <p>
	 * Locking: message write, subscription write.
	 */
	void removeSubscription(T txn, GroupId g) throws DbException;

	/**
	 * Removes a transport (and all associated state) from the database.
	 * <p>
	 * Locking: transport write, window write.
	 */
	void removeTransport(T txn, TransportId t) throws DbException;

	/**
	 * Makes the given group invisible to the given contact.
	 * <p>
	 * Locking: subscription write.
	 */
	void removeVisibility(T txn, ContactId c, GroupId g) throws DbException;

	/**
	 * Sets the connection reordering window for the given endpoint in the
	 * given rotation period.
	 * <p>
	 * Locking: window write.
	 */
	void setConnectionWindow(T txn, ContactId c, TransportId t, long period,
			long centre, byte[] bitmap) throws DbException;

	/**
	 * Sets the time at which a connection to the given contact was last made.
	 * <p>
	 * Locking: window write.
	 */
	void setLastConnected(T txn, ContactId c, long now) throws DbException;

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
	boolean setReadFlag(T txn, MessageId m, boolean read) throws DbException;

	/**
	 * Sets the remote transport properties for the given contact, replacing
	 * any existing properties.
	 * <p>
	 * Locking: transport write.
	 */
	void setRemoteProperties(T txn, ContactId c,
			Map<TransportId, TransportProperties> p) throws DbException;

	/**
	 * Updates the remote transport properties for the given contact and the
	 * given transport, replacing any existing properties, unless an update
	 * with an equal or higher version number has already been received from
	 * the contact.
	 * <p>
	 * Locking: transport write.
	 */
	void setRemoteProperties(T txn, ContactId c, TransportId t,
			TransportProperties p, long version) throws DbException;

	/**
	 * Sets the retention time of the given contact's database, unless an
	 * update with an equal or higher version number has already been received
	 * from the contact.
	 * <p>
	 * Locking: retention write.
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
	boolean setStarredFlag(T txn, MessageId m, boolean starred)
			throws DbException;

	/**
	 * If the database contains the given message and it belongs to a group
	 * that is visible to the given contact, marks the message as seen by the
	 * contact and returns true; otherwise returns false.
	 * <p>
	 * Locking: message write, subscription read.
	 */
	boolean setStatusSeenIfVisible(T txn, ContactId c, MessageId m)
			throws DbException;

	/**
	 * Updates the groups to which the given contact subscribes, unless an
	 * update with an equal or higher version number has already been received
	 * from the contact.
	 * <p>
	 * Locking: subscription write.
	 */
	void setSubscriptions(T txn, ContactId c, Collection<Group> subs,
			long version) throws DbException;

	/**
	 * Records a retention ack from the given contact for the given version
	 * unless the contact has already acked an equal or higher version.
	 * <p>
	 * Locking: retention write.
	 */
	void setRetentionUpdateAcked(T txn, ContactId c, long version)
			throws DbException;

	/**
	 * Records a subscription ack from the given contact for the given version
	 * unless the contact has already acked an equal or higher version.
	 * <p>
	 * Locking: subscription write.
	 */
	void setSubscriptionUpdateAcked(T txn, ContactId c, long version)
			throws DbException;

	/**
	 * Records a transport ack from the give contact for the given version
	 * unless the contact has already acked an equal or higher version.
	 * <p>
	 * Locking: transport write.
	 */
	void setTransportUpdateAcked(T txn, ContactId c, TransportId t,
			long version) throws DbException;

	/**
	 * Makes the given group visible or invisible to future contacts by default.
	 * <p>
	 * Locking: subscription write.
	 */
	void setVisibleToAll(T txn, GroupId g, boolean visible) throws DbException;

	/**
	 * Updates the expiry times of the given messages with respect to the given
	 * contact, using the given transmission counts and the latency of the
	 * transport over which they were sent.
	 * <p>
	 * Locking: message write.
	 */
	void updateExpiryTimes(T txn, ContactId c, Map<MessageId, Integer> sent,
			long maxLatency) throws DbException;
}
