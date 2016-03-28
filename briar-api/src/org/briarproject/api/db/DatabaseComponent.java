package org.briarproject.api.db;

import org.briarproject.api.DeviceId;
import org.briarproject.api.TransportId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.settings.Settings;
import org.briarproject.api.sync.Ack;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageStatus;
import org.briarproject.api.sync.Offer;
import org.briarproject.api.sync.Request;
import org.briarproject.api.transport.TransportKeys;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Encapsulates the database implementation and exposes high-level operations
 * to other components.
 * <p/>
 * This interface's methods are blocking, but they do not call out into other
 * components except to broadcast {@link org.briarproject.api.event.Event
 * Events}, so they can safely be called while holding locks.
 */
public interface DatabaseComponent {

	/**
	 * Opens the database and returns true if the database already existed.
	 */
	boolean open() throws DbException;

	/**
	 * Waits for any open transactions to finish and closes the database.
	 */
	void close() throws DbException, IOException;

	/**
	 * Starts a new transaction and returns an object representing it.
	 */
	Transaction startTransaction() throws DbException;

	/**
	 * Ends a transaction. If the transaction is marked as complete, the
	 * transaction is committed and any events attached to the transaction are
	 * broadcast; otherwise the transaction is aborted.
	 */
	void endTransaction(Transaction txn) throws DbException;

	/**
	 * Stores a contact associated with the given local and remote pseudonyms,
	 * and returns an ID for the contact.
	 */
	ContactId addContact(Transaction txn, Author remote, AuthorId local,
			boolean active) throws DbException;

	/**
	 * Stores a group.
	 */
	void addGroup(Transaction txn, Group g) throws DbException;

	/**
	 * Stores a local pseudonym.
	 */
	void addLocalAuthor(Transaction txn, LocalAuthor a) throws DbException;

	/**
	 * Stores a local message.
	 */
	void addLocalMessage(Transaction txn, Message m, ClientId c, Metadata meta,
			boolean shared) throws DbException;

	/**
	 * Stores a transport.
	 */
	void addTransport(Transaction txn, TransportId t, int maxLatency)
			throws DbException;

	/**
	 * Stores transport keys for a newly added contact.
	 */
	void addTransportKeys(Transaction txn, ContactId c, TransportKeys k)
			throws DbException;

	/**
	 * Deletes the message with the given ID. The message ID and any other
	 * associated data are not deleted.
	 */
	void deleteMessage(Transaction txn, MessageId m) throws DbException;

	/** Deletes any metadata associated with the given message. */
	void deleteMessageMetadata(Transaction txn, MessageId m) throws DbException;

	/**
	 * Returns an acknowledgement for the given contact, or null if there are
	 * no messages to acknowledge.
	 */
	Ack generateAck(Transaction txn, ContactId c, int maxMessages)
			throws DbException;

	/**
	 * Returns a batch of raw messages for the given contact, with a total
	 * length less than or equal to the given length, for transmission over a
	 * transport with the given maximum latency. Returns null if there are no
	 * sendable messages that fit in the given length.
	 */
	Collection<byte[]> generateBatch(Transaction txn, ContactId c,
			int maxLength, int maxLatency) throws DbException;

	/**
	 * Returns an offer for the given contact for transmission over a
	 * transport with the given maximum latency, or null if there are no
	 * messages to offer.
	 */
	Offer generateOffer(Transaction txn, ContactId c, int maxMessages,
			int maxLatency) throws DbException;

	/**
	 * Returns a request for the given contact, or null if there are no
	 * messages to request.
	 */
	Request generateRequest(Transaction txn, ContactId c, int maxMessages)
			throws DbException;

	/**
	 * Returns a batch of raw messages for the given contact, with a total
	 * length less than or equal to the given length, for transmission over a
	 * transport with the given maximum latency. Only messages that have been
	 * requested by the contact are returned. Returns null if there are no
	 * sendable messages that fit in the given length.
	 */
	Collection<byte[]> generateRequestedBatch(Transaction txn, ContactId c,
			int maxLength, int maxLatency) throws DbException;

	/**
	 * Returns the contact with the given ID.
	 */
	Contact getContact(Transaction txn, ContactId c) throws DbException;

	/**
	 * Returns all contacts.
	 */
	Collection<Contact> getContacts(Transaction txn) throws DbException;

	/**
	 * Returns all contacts associated with the given local pseudonym.
	 */
	Collection<ContactId> getContacts(Transaction txn, AuthorId a)
			throws DbException;

	/**
	 * Returns the unique ID for this device.
	 */
	DeviceId getDeviceId(Transaction txn) throws DbException;

	/**
	 * Returns the group with the given ID.
	 */
	Group getGroup(Transaction txn, GroupId g) throws DbException;

	/**
	 * Returns the metadata for the given group.
	 */
	Metadata getGroupMetadata(Transaction txn, GroupId g) throws DbException;

	/**
	 * Returns all groups belonging to the given client.
	 */
	Collection<Group> getGroups(Transaction txn, ClientId c) throws DbException;

	/**
	 * Returns the local pseudonym with the given ID.
	 */
	LocalAuthor getLocalAuthor(Transaction txn, AuthorId a) throws DbException;

	/**
	 * Returns all local pseudonyms.
	 */
	Collection<LocalAuthor> getLocalAuthors(Transaction txn) throws DbException;

	/**
	 * Returns the IDs of any messages that need to be validated by the given
	 * client.
	 */
	Collection<MessageId> getMessagesToValidate(Transaction txn, ClientId c)
			throws DbException;

	/**
	 * Returns the message with the given ID, in serialised form.
	 */
	byte[] getRawMessage(Transaction txn, MessageId m) throws DbException;

	/**
	 * Returns the metadata for all messages in the given group.
	 */
	Map<MessageId, Metadata> getMessageMetadata(Transaction txn, GroupId g)
			throws DbException;

	/**
	 * Returns the metadata for the given message.
	 */
	Metadata getMessageMetadata(Transaction txn, MessageId m)
			throws DbException;

	/**
	 * Returns the status of all messages in the given group with respect to
	 * the given contact.
	 */
	Collection<MessageStatus> getMessageStatus(Transaction txn, ContactId c,
			GroupId g) throws DbException;

	/**
	 * Returns the status of the given message with respect to the given
	 * contact.
	 */
	MessageStatus getMessageStatus(Transaction txn, ContactId c, MessageId m)
			throws DbException;

	/**
	 * Returns all settings in the given namespace.
	 */
	Settings getSettings(Transaction txn, String namespace) throws DbException;

	/**
	 * Returns all transport keys for the given transport.
	 */
	Map<ContactId, TransportKeys> getTransportKeys(Transaction txn,
			TransportId t) throws DbException;

	/**
	 * Increments the outgoing stream counter for the given contact and
	 * transport in the given rotation period .
	 */
	void incrementStreamCounter(Transaction txn, ContactId c, TransportId t,
			long rotationPeriod) throws DbException;

	/**
	 * Returns true if the given group is visible to the given contact.
	 */
	boolean isVisibleToContact(Transaction txn, ContactId c, GroupId g)
			throws DbException;

	/**
	 * Merges the given metadata with the existing metadata for the given
	 * group.
	 */
	void mergeGroupMetadata(Transaction txn, GroupId g, Metadata meta)
			throws DbException;

	/**
	 * Merges the given metadata with the existing metadata for the given
	 * message.
	 */
	void mergeMessageMetadata(Transaction txn, MessageId m, Metadata meta)
			throws DbException;

	/**
	 * Merges the given settings with the existing settings in the given
	 * namespace.
	 */
	void mergeSettings(Transaction txn, Settings s, String namespace)
			throws DbException;

	/**
	 * Processes an ack from the given contact.
	 */
	void receiveAck(Transaction txn, ContactId c, Ack a) throws DbException;

	/**
	 * Processes a message from the given contact.
	 */
	void receiveMessage(Transaction txn, ContactId c, Message m)
			throws DbException;

	/**
	 * Processes an offer from the given contact.
	 */
	void receiveOffer(Transaction txn, ContactId c, Offer o) throws DbException;

	/**
	 * Processes a request from the given contact.
	 */
	void receiveRequest(Transaction txn, ContactId c, Request r)
			throws DbException;

	/**
	 * Removes a contact (and all associated state) from the database.
	 */
	void removeContact(Transaction txn, ContactId c) throws DbException;

	/**
	 * Removes a group (and all associated state) from the database.
	 */
	void removeGroup(Transaction txn, Group g) throws DbException;

	/**
	 * Removes a local pseudonym (and all associated state) from the database.
	 */
	void removeLocalAuthor(Transaction txn, AuthorId a) throws DbException;

	/**
	 * Removes a transport (and all associated state) from the database.
	 */
	void removeTransport(Transaction txn, TransportId t) throws DbException;

	/**
	 * Marks the given contact as active or inactive.
	 */
	void setContactActive(Transaction txn, ContactId c, boolean active)
		throws DbException;

	/**
	 * Marks the given message as shared or unshared.
	 */
	void setMessageShared(Transaction txn, Message m, boolean shared)
			throws DbException;

	/**
	 * Marks the given message as valid or invalid.
	 */
	void setMessageValid(Transaction txn, Message m, ClientId c, boolean valid)
			throws DbException;

	/**
	 * Sets the reordering window for the given contact and transport in the
	 * given rotation period.
	 */
	void setReorderingWindow(Transaction txn, ContactId c, TransportId t,
			long rotationPeriod, long base, byte[] bitmap) throws DbException;

	/**
	 * Makes a group visible or invisible to a contact.
	 */
	void setVisibleToContact(Transaction txn, ContactId c, GroupId g,
			boolean visible) throws DbException;

	/**
	 * Stores the given transport keys, deleting any keys they have replaced.
	 */
	void updateTransportKeys(Transaction txn,
			Map<ContactId, TransportKeys> keys) throws DbException;
}
