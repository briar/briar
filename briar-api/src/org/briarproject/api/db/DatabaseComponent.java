package org.briarproject.api.db;

import org.briarproject.api.Settings;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.Ack;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageStatus;
import org.briarproject.api.sync.Offer;
import org.briarproject.api.sync.Request;
import org.briarproject.api.sync.SubscriptionAck;
import org.briarproject.api.sync.SubscriptionUpdate;
import org.briarproject.api.sync.TransportAck;
import org.briarproject.api.sync.TransportUpdate;
import org.briarproject.api.transport.TransportKeys;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Encapsulates the database implementation and exposes high-level operations
 * to other components.
 * <p>
 * This interface's methods are blocking, but they do not call out into other
 * components except to broadcast {@link org.briarproject.api.event.Event
 * Events}, so they can safely be called while holding locks.
 */
public interface DatabaseComponent {

	/** Opens the database and returns true if the database already existed. */
	boolean open() throws DbException, IOException;

	/** Waits for any open transactions to finish and closes the database. */
	void close() throws DbException, IOException;

	/**
	 * Stores a contact associated with the given local and remote pseudonyms,
	 * and returns an ID for the contact.
	 */
	ContactId addContact(Author remote, AuthorId local) throws DbException;

	/** Adds a group to the given contact's subscriptions. */
	void addContactGroup(ContactId c, Group g) throws DbException;

	/**
	 * Subscribes to a group, or returns false if the user already has the
	 * maximum number of subscriptions.
	 */
	boolean addGroup(Group g) throws DbException;

	/** Stores a local pseudonym. */
	void addLocalAuthor(LocalAuthor a) throws DbException;

	/** Stores a local message. */
	void addLocalMessage(Message m, ClientId c, Metadata meta)
			throws DbException;

	/**
	 * Stores a transport and returns true if the transport was not previously
	 * in the database.
	 */
	boolean addTransport(TransportId t, int maxLatency) throws DbException;

	/**
	 * Stores the given transport keys for a newly added contact.
	 */
	void addTransportKeys(ContactId c, TransportKeys k) throws DbException;

	/**
	 * Returns an acknowledgement for the given contact, or null if there are
	 * no messages to acknowledge.
	 */
	Ack generateAck(ContactId c, int maxMessages) throws DbException;

	/**
	 * Returns a batch of raw messages for the given contact, with a total
	 * length less than or equal to the given length, for transmission over a
	 * transport with the given maximum latency. Returns null if there are no
	 * sendable messages that fit in the given length.
	 */
	Collection<byte[]> generateBatch(ContactId c, int maxLength,
			int maxLatency) throws DbException;

	/**
	 * Returns an offer for the given contact for transmission over a
	 * transport with the given maximum latency, or null if there are no
	 * messages to offer.
	 */
	Offer generateOffer(ContactId c, int maxMessages, int maxLatency)
			throws DbException;

	/**
	 * Returns a request for the given contact, or null if there are no
	 * messages to request.
	 */
	Request generateRequest(ContactId c, int maxMessages) throws DbException;

	/**
	 * Returns a batch of raw messages for the given contact, with a total
	 * length less than or equal to the given length, for transmission over a
	 * transport with the given maximum latency. Only messages that have been
	 * requested by the contact are returned. Returns null if there are no
	 * sendable messages that fit in the given length.
	 */
	Collection<byte[]> generateRequestedBatch(ContactId c, int maxLength,
			int maxLatency) throws DbException;

	/**
	 * Returns a subscription ack for the given contact, or null if no
	 * subscription ack is due.
	 */
	SubscriptionAck generateSubscriptionAck(ContactId c) throws DbException;

	/**
	 * Returns a subscription update for the given contact, for transmission
	 * over a transport with the given latency. Returns null if no update is
	 * due.
	 */
	SubscriptionUpdate generateSubscriptionUpdate(ContactId c, int maxLatency)
			throws DbException;

	/**
	 * Returns a batch of transport acks for the given contact, or null if no
	 * transport acks are due.
	 */
	Collection<TransportAck> generateTransportAcks(ContactId c)
			throws DbException;

	/**
	 * Returns a batch of transport updates for the given contact, for
	 * transmission over a transport with the given latency. Returns null if no
	 * updates are due.
	 */
	Collection<TransportUpdate> generateTransportUpdates(ContactId c,
			int maxLatency) throws DbException;

	/**
	 * Returns all groups belonging to the given client to which the user could
	 * subscribe.
	 */
	Collection<Group> getAvailableGroups(ClientId c) throws DbException;

	/** Returns the contact with the given ID. */
	Contact getContact(ContactId c) throws DbException;

	/** Returns all contacts. */
	Collection<Contact> getContacts() throws DbException;

	/** Returns all contacts associated with the given local pseudonym. */
	Collection<ContactId> getContacts(AuthorId a) throws DbException;

	/** Returns the group with the given ID, if the user subscribes to it. */
	Group getGroup(GroupId g) throws DbException;

	/** Returns the metadata for the given group. */
	Metadata getGroupMetadata(GroupId g) throws DbException;

	/**
	 * Returns all groups belonging to the given client to which the user
	 * subscribes.
	 */
	Collection<Group> getGroups(ClientId c) throws DbException;

	/** Returns the local pseudonym with the given ID. */
	LocalAuthor getLocalAuthor(AuthorId a) throws DbException;

	/** Returns all local pseudonyms. */
	Collection<LocalAuthor> getLocalAuthors() throws DbException;

	/** Returns the local transport properties for all transports. */
	Map<TransportId, TransportProperties> getLocalProperties()
			throws DbException;

	/** Returns the local transport properties for the given transport. */
	TransportProperties getLocalProperties(TransportId t) throws DbException;

	/**
	 * Returns the IDs of any messages that need to be validated by the given
	 * client.
	 */
	Collection<MessageId> getMessagesToValidate(ClientId c) throws DbException;

	/** Returns the message with the given ID, in serialised form. */
	byte[] getRawMessage(MessageId m) throws DbException;

	/** Returns the metadata for all messages in the given group. */
	Map<MessageId, Metadata> getMessageMetadata(GroupId g)
			throws DbException;

	/** Returns the metadata for the given message. */
	Metadata getMessageMetadata(MessageId m) throws DbException;

	/**
	 * Returns the status of all messages in the given group with respect to
	 * the given contact.
	 */
	Collection<MessageStatus> getMessageStatus(ContactId c, GroupId g)
			throws DbException;

	/**
	 * Returns the status of the given message with respect to the given
	 * contact.
	 */
	MessageStatus getMessageStatus(ContactId c, MessageId m)
			throws DbException;

	/** Returns all remote transport properties for the given transport. */
	Map<ContactId, TransportProperties> getRemoteProperties(TransportId t)
			throws DbException;

	/** Returns all settings in the given namespace. */
	Settings getSettings(String namespace) throws DbException;

	/** Returns all contacts who subscribe to the given group. */
	Collection<Contact> getSubscribers(GroupId g) throws DbException;

	/** Returns all transport keys for the given transport. */
	Map<ContactId, TransportKeys> getTransportKeys(TransportId t)
			throws DbException;

	/** Returns the maximum latencies in milliseconds of all transports. */
	Map<TransportId, Integer> getTransportLatencies() throws DbException;

	/** Returns the IDs of all contacts to which the given group is visible. */
	Collection<ContactId> getVisibility(GroupId g) throws DbException;

	/**
	 * Increments the outgoing stream counter for the given contact and
	 * transport in the given rotation period .
	 */
	void incrementStreamCounter(ContactId c, TransportId t, long rotationPeriod)
			throws DbException;

	/**
	 * Merges the given metadata with the existing metadata for the given
	 * group.
	 */
	void mergeGroupMetadata(GroupId g, Metadata meta) throws DbException;

	/**
	 * Merges the given properties with the existing local properties for the
	 * given transport.
	 */
	void mergeLocalProperties(TransportId t, TransportProperties p)
			throws DbException;

	/**
	 * Merges the given metadata with the existing metadata for the given
	 * message.
	 */
	void mergeMessageMetadata(MessageId m, Metadata meta) throws DbException;

	/**
	 * Merges the given settings with the existing settings in the given
	 * namespace.
	 */
	void mergeSettings(Settings s, String namespace) throws DbException;

	/** Processes an ack from the given contact. */
	void receiveAck(ContactId c, Ack a) throws DbException;

	/** Processes a message from the given contact. */
	void receiveMessage(ContactId c, Message m) throws DbException;

	/** Processes an offer from the given contact. */
	void receiveOffer(ContactId c, Offer o) throws DbException;

	/** Processes a request from the given contact. */
	void receiveRequest(ContactId c, Request r) throws DbException;

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
	 * Unsubscribes from a group. Any messages belonging to the group
	 * are deleted from the database.
	 */
	void removeGroup(Group g) throws DbException;

	/**
	 * Removes a local pseudonym (and all associated state) from the database.
	 */
	void removeLocalAuthor(AuthorId a) throws DbException;

	/**
	 * Removes a transport (and any associated configuration and local
	 * properties) from the database.
	 */
	void removeTransport(TransportId t) throws DbException;

	/** Sets the status of the given contact. */
	void setContactStatus(ContactId c, StorageStatus s) throws DbException;

	/** Sets the status of the given local pseudonym. */
	void setLocalAuthorStatus(AuthorId a, StorageStatus s)
		throws DbException;

	/** Marks the given message as valid or invalid. */
	void setMessageValidity(Message m, ClientId c, boolean valid)
			throws DbException;

	/**
	 * Sets the remote transport properties for the given contact, replacing
	 * any existing properties.
	 */
	void setRemoteProperties(ContactId c,
			Map<TransportId, TransportProperties> p) throws DbException;

	/**
	 * Sets the reordering window for the given contact and transport in the
	 * given rotation period.
	 */
	void setReorderingWindow(ContactId c, TransportId t, long rotationPeriod,
			long base, byte[] bitmap) throws DbException;

	/**
	 * Makes a group visible to the given set of contacts and invisible to any
	 * other current or future contacts.
	 */
	void setVisibility(GroupId g, Collection<ContactId> visible)
			throws DbException;

	/**
	 * Makes a group visible to all current and future contacts, or invisible
	 * to future contacts.
	 */
	void setVisibleToAll(GroupId g, boolean all) throws DbException;

	/**
	 * Stores the given transport keys, deleting any keys they have replaced.
	 */
	void updateTransportKeys(Map<ContactId, TransportKeys> keys)
			throws DbException;
}
