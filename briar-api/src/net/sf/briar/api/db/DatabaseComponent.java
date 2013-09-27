package net.sf.briar.api.db;

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
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.messaging.Ack;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.GroupStatus;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.messaging.Offer;
import net.sf.briar.api.messaging.Rating;
import net.sf.briar.api.messaging.Request;
import net.sf.briar.api.messaging.RetentionAck;
import net.sf.briar.api.messaging.RetentionUpdate;
import net.sf.briar.api.messaging.SubscriptionAck;
import net.sf.briar.api.messaging.SubscriptionUpdate;
import net.sf.briar.api.messaging.TransportAck;
import net.sf.briar.api.messaging.TransportUpdate;
import net.sf.briar.api.transport.Endpoint;
import net.sf.briar.api.transport.TemporarySecret;

/**
 * Encapsulates the database implementation and exposes high-level operations
 * to other components.
 */
public interface DatabaseComponent {

	/** Opens the database and returns true if the database already existed. */
	boolean open() throws DbException, IOException;

	/** Waits for any open transactions to finish and closes the database. */
	void close() throws DbException, IOException;

	/** Adds a listener to be notified when database events occur. */
	void addListener(DatabaseListener d);

	/** Removes a listener. */
	void removeListener(DatabaseListener d);

	/**
	 * Stores a contact with the given pseudonym, associated with the given
	 * local pseudonym, and returns an ID for the contact.
	 */
	ContactId addContact(Author remote, AuthorId local) throws DbException;

	/** Stores an endpoint. */
	void addEndpoint(Endpoint ep) throws DbException;

	/** Stores a pseudonym that the user can use to sign messages. */
	void addLocalAuthor(LocalAuthor a) throws DbException;

	/** Stores a locally generated group message. */
	void addLocalGroupMessage(Message m) throws DbException;

	/** Stores a locally generated private message. */
	void addLocalPrivateMessage(Message m, ContactId c) throws DbException;

	/**
	 * Stores the given temporary secrets and deletes any secrets that have
	 * been made obsolete.
	 */
	void addSecrets(Collection<TemporarySecret> secrets) throws DbException;

	/**
	 * Stores a transport and returns true if the transport was not previously
	 * in the database.
	 */
	boolean addTransport(TransportId t, long maxLatency) throws DbException;

	/**
	 * Generates an acknowledgement for the given contact, or returns null if
	 * there are no messages to acknowledge.
	 */
	Ack generateAck(ContactId c, int maxMessages) throws DbException;

	/**
	 * Generates a batch of raw messages for the given contact, with a total
	 * length less than or equal to the given length, for transmission over a
	 * transport with the given maximum latency. Returns null if there are no
	 * sendable messages that fit in the given length.
	 */
	Collection<byte[]> generateBatch(ContactId c, int maxLength,
			long maxLatency) throws DbException;

	/**
	 * Generates a batch of raw messages for the given contact from the given
	 * collection of requested messages, with a total length less than or equal
	 * to the given length, for transmission over a transport with the given
	 * maximum latency. Any messages that were either added to the batch, or
	 * were considered but are no longer sendable to the contact, are removed
	 * from the collection of requested messages before returning. Returns null
	 * if there are no sendable messages that fit in the given length.
	 */
	Collection<byte[]> generateBatch(ContactId c, int maxLength,
			long maxLatency, Collection<MessageId> requested)
					throws DbException;

	/**
	 * Generates an offer for the given contact, or returns null if there are
	 * no messages to offer.
	 */
	Offer generateOffer(ContactId c, int maxMessages) throws DbException;

	/**
	 * Generates a retention ack for the given contact, or returns null if no
	 * ack is due.
	 */
	RetentionAck generateRetentionAck(ContactId c) throws DbException;

	/**
	 * Generates a retention update for the given contact, for transmission
	 * over a transport with the given latency. Returns null if no update is
	 * due.
	 */
	RetentionUpdate generateRetentionUpdate(ContactId c, long maxLatency)
			throws DbException;

	/**
	 * Generates a subscription ack for the given contact, or returns null if
	 * no ack is due.
	 */
	SubscriptionAck generateSubscriptionAck(ContactId c) throws DbException;

	/**
	 * Generates a subscription update for the given contact, for transmission
	 * over a transport with the given latency. Returns null if no update is
	 * due.
	 */
	SubscriptionUpdate generateSubscriptionUpdate(ContactId c, long maxLatency)
			throws DbException;

	/**
	 * Generates a batch of transport acks for the given contact, or returns
	 * null if no acks are due.
	 */
	Collection<TransportAck> generateTransportAcks(ContactId c)
			throws DbException;

	/**
	 * Generates a batch of transport updates for the given contact, for
	 * transmission over a transport with the given latency. Returns null if no
	 * updates are due.
	 */
	Collection<TransportUpdate> generateTransportUpdates(ContactId c,
			long maxLatency) throws DbException;

	/** Returns the status of all groups to which the user can subscribe. */
	Collection<GroupStatus> getAvailableGroups() throws DbException;

	/** Returns the configuration for the given transport. */
	TransportConfig getConfig(TransportId t) throws DbException;

	/** Returns the contact with the given ID. */
	Contact getContact(ContactId c) throws DbException;

	/** Returns all contacts. */
	Collection<Contact> getContacts() throws DbException;

	/** Returns the group with the given ID, if the user subscribes to it. */
	Group getGroup(GroupId g) throws DbException;

	/** Returns the headers of all messages in the given group. */
	Collection<GroupMessageHeader> getGroupMessageHeaders(GroupId g)
			throws DbException;

	/**
	 * Returns the time at which a connection to each contact was last opened
	 * or closed.
	 */
	Map<ContactId, Long> getLastConnected() throws DbException;

	/** Returns the pseudonym with the given ID. */
	LocalAuthor getLocalAuthor(AuthorId a) throws DbException;

	/** Returns all pseudonyms that the user can use to sign messages. */
	Collection<LocalAuthor> getLocalAuthors() throws DbException;

	/** Returns the local transport properties for all transports. */
	Map<TransportId, TransportProperties> getLocalProperties()
			throws DbException;

	/** Returns the local transport properties for the given transport. */
	TransportProperties getLocalProperties(TransportId t) throws DbException;

	/** Returns the body of the message with the given ID. */
	byte[] getMessageBody(MessageId m) throws DbException;

	/**
	 * Returns the headers of all private messages to or from the given
	 * contact.
	 */
	Collection<PrivateMessageHeader> getPrivateMessageHeaders(ContactId c)
			throws DbException;

	/** Returns the user's rating for the given author. */
	Rating getRating(AuthorId a) throws DbException;

	/** Returns true if the given message has been read. */
	boolean getReadFlag(MessageId m) throws DbException;

	/** Returns all remote transport properties for the given transport. */
	Map<ContactId, TransportProperties> getRemoteProperties(TransportId t)
			throws DbException;

	/** Returns all temporary secrets. */
	Collection<TemporarySecret> getSecrets() throws DbException;

	/** Returns true if the given message has been starred. */
	boolean getStarredFlag(MessageId m) throws DbException;

	/** Returns the set of groups to which the user subscribes. */
	Collection<Group> getSubscriptions() throws DbException;

	/** Returns the maximum latencies of all local transports. */
	Map<TransportId, Long> getTransportLatencies() throws DbException;

	/** Returns the number of unread messages in each subscribed group. */
	Map<GroupId, Integer> getUnreadMessageCounts() throws DbException;

	/** Returns the contacts to which the given group is visible. */
	Collection<ContactId> getVisibility(GroupId g) throws DbException;

	/** Returns the subscriptions that are visible to the given contact. */
	Collection<GroupId> getVisibleSubscriptions(ContactId c) throws DbException;

	/** Returns true if any messages are sendable to the given contact. */
	boolean hasSendableMessages(ContactId c) throws DbException;

	/**
	 * Increments the outgoing connection counter for the given endpoint
	 * in the given rotation period and returns the old value, or -1 if the
	 * counter does not exist.
	 */
	long incrementConnectionCounter(ContactId c, TransportId t, long period)
			throws DbException;

	/**
	 * Merges the given configuration with existing configuration for the
	 * given transport.
	 */
	void mergeConfig(TransportId t, TransportConfig c) throws DbException;

	/**
	 * Merges the given properties with the existing local properties for the
	 * given transport.
	 */
	void mergeLocalProperties(TransportId t, TransportProperties p)
			throws DbException;

	/** Processes an ack from the given contact. */
	void receiveAck(ContactId c, Ack a) throws DbException;

	/** Processes a message from the given contact. */
	void receiveMessage(ContactId c, Message m) throws DbException;

	/**
	 * Processes an offer from the given contact and generates a request for
	 * any messages in the offer that the contact should send. To prevent
	 * contacts from using offers to test for subscriptions that are not
	 * visible to them, any messages belonging to groups that are not visible
	 * to the contact are requested just as though they were not present in the
	 * database.
	 */
	Request receiveOffer(ContactId c, Offer o) throws DbException;

	/** Processes a retention ack from the given contact. */
	void receiveRetentionAck(ContactId c, RetentionAck a) throws DbException;

	/** Processes a retention update from the given contact. */
	void receiveRetentionUpdate(ContactId c, RetentionUpdate u)
			throws DbException;

	/** Processes a subscription ack from the given contact. */
	void receiveSubscriptionAck(ContactId c, SubscriptionAck a)
			throws DbException;

	/** Processes a subscription update from the given contact. */
	void receiveSubscriptionUpdate(ContactId c, SubscriptionUpdate u)
			throws DbException;

	/** Processes a transport ack from the given contact. */
	void receiveTransportAck(ContactId c, TransportAck a) throws DbException;

	/** Processes a transport update from the given contact. */
	void receiveTransportUpdate(ContactId c, TransportUpdate u)
			throws DbException;

	/** Removes a contact (and all associated state) from the database. */
	void removeContact(ContactId c) throws DbException;

	/**
	 * Removes a transport (and any associated configuration and local
	 * properties) from the database.
	 */
	void removeTransport(TransportId t) throws DbException;

	/**
	 * Sets the connection reordering window for the given endoint in the given
	 * rotation period.
	 */
	void setConnectionWindow(ContactId c, TransportId t, long period,
			long centre, byte[] bitmap) throws DbException;

	/** Records the user's rating for the given author. */
	void setRating(AuthorId a, Rating r) throws DbException;

	/**
	 * Marks the given message read or unread and returns true if it was
	 * previously read.
	 */
	boolean setReadFlag(MessageId m, boolean read) throws DbException;

	/**
	 * Sets the remote transport properties for the given contact, replacing
	 * any existing properties.
	 */
	void setRemoteProperties(ContactId c,
			Map<TransportId, TransportProperties> p) throws DbException;

	/** Records the given messages as having been seen by the given contact. */
	void setSeen(ContactId c, Collection<MessageId> seen) throws DbException;

	/**
	 * Marks the given message starred or unstarred and returns true if it was
	 * previously starred.
	 */
	boolean setStarredFlag(MessageId m, boolean starred) throws DbException;

	/**
	 * Makes the given group visible to the given set of contacts and invisible
	 * to any other current or future contacts.
	 */
	void setVisibility(GroupId g, Collection<ContactId> visible)
			throws DbException;

	/**
	 * Makes the given group visible or invisible to future contacts by default.
	 * If <tt>visible</tt> is true, the group is also made visible to all
	 * current contacts.
	 */
	void setVisibleToAll(GroupId g, boolean all) throws DbException;

	/**
	 * Subscribes to the given group, or returns false if the user already has
	 * the maximum number of subscriptions.
	 */
	boolean subscribe(Group g) throws DbException;

	/**
	 * Unsubscribes from the given group. Any messages belonging to the group
	 * are deleted from the database.
	 */
	void unsubscribe(Group g) throws DbException;
}
