package net.sf.briar.api.db;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.protocol.writers.OfferWriter;
import net.sf.briar.api.protocol.writers.RequestWriter;
import net.sf.briar.api.protocol.writers.SubscriptionWriter;
import net.sf.briar.api.protocol.writers.TransportWriter;
import net.sf.briar.api.transport.ConnectionWindow;

/**
 * Encapsulates the database implementation and exposes high-level operations
 * to other components.
 */
public interface DatabaseComponent {

	/**
	 * Opens the database.
	 * @param resume True to reopen an existing database or false to create a
	 * new one.
	 */
	void open(boolean resume) throws DbException;

	/** Waits for any open transactions to finish and closes the database. */
	void close() throws DbException;

	/** Adds a listener to be notified when database events occur. */
	void addListener(DatabaseListener d);

	/** Removes a listener. */
	void removeListener(DatabaseListener d);

	/**
	 * Adds a new contact to the database with the given transport properties
	 * and shared secret, returns an ID for the contact.
	 */
	ContactId addContact(Map<TransportId, Map<String, String>> transports,
			byte[] secret) throws DbException;

	/** Adds a locally generated group message to the database. */
	void addLocalGroupMessage(Message m) throws DbException;

	/** Adds a locally generated private message to the database. */
	void addLocalPrivateMessage(Message m, ContactId c) throws DbException;

	/**
	 * Generates an acknowledgement for the given contact.
	 * @return True if any batch IDs were added to the acknowledgement.
	 */
	boolean generateAck(ContactId c, AckWriter a) throws DbException,
	IOException;

	/**
	 * Generates a batch of messages for the given contact.
	 * @return True if any messages were added to tbe batch.
	 */
	boolean generateBatch(ContactId c, BatchWriter b) throws DbException,
	IOException;

	/**
	 * Generates a batch of messages for the given contact from the given
	 * collection of requested messages. Any messages that were either added to
	 * the batch, or were considered but are no longer sendable to the contact,
	 * are removed from the collection of requested messages before returning.
	 * @return True if any messages were added to the batch.
	 */
	boolean generateBatch(ContactId c, BatchWriter b,
			Collection<MessageId> requested) throws DbException, IOException;

	/**
	 * Generates an offer for the given contact and returns the offered
	 * message IDs.
	 */
	Collection<MessageId> generateOffer(ContactId c, OfferWriter o)
	throws DbException, IOException;

	/** Generates a subscription update for the given contact. */
	void generateSubscriptionUpdate(ContactId c, SubscriptionWriter s) throws
	DbException, IOException;

	/** Generates a transport update for the given contact. */
	void generateTransportUpdate(ContactId c, TransportWriter t) throws
	DbException, IOException;

	/**
	 * Returns an outgoing connection number for the given contact and
	 * transport.
	 */
	long getConnectionNumber(ContactId c, TransportId t) throws DbException;

	/**
	 * Returns the connection reordering window for the given contact and
	 * transport.
	 */
	ConnectionWindow getConnectionWindow(ContactId c, TransportId t)
	throws DbException;

	/** Returns the IDs of all contacts. */
	Collection<ContactId> getContacts() throws DbException;

	/** Returns the user's rating for the given author. */
	Rating getRating(AuthorId a) throws DbException;

	/** Returns the secret shared with the given contact. */
	byte[] getSharedSecret(ContactId c) throws DbException;

	/** Returns the set of groups to which the user subscribes. */
	Collection<Group> getSubscriptions() throws DbException;

	/** Returns the configuration for the given transport. */
	Map<String, String> getTransportConfig(TransportId t) throws DbException;

	/** Returns all local transport properties. */
	Map<TransportId, Map<String, String>> getTransports() throws DbException;

	/** Returns all transport properties for the given contact. */
	Map<TransportId, Map<String, String>> getTransports(ContactId c)
	throws DbException;

	/** Returns the contacts to which the given group is visible. */
	Collection<ContactId> getVisibility(GroupId g) throws DbException;

	/** Returns true if any messages are sendable to the given contact. */
	boolean hasSendableMessages(ContactId c) throws DbException;

	/** Processes an acknowledgement from the given contact. */
	void receiveAck(ContactId c, Ack a) throws DbException;

	/** Processes a batch of messages from the given contact. */
	void receiveBatch(ContactId c, Batch b) throws DbException;

	/**
	 * Processes an offer from the given contact and generates a request for
	 * any messages in the offer that the contact should send. To prevent
	 * contacts from using offers to test for subscriptions that are not
	 * visible to them, any messages belonging to groups that are not visible
	 * to the contact are requested just as though they were not present in the
	 * database.
	 */
	void receiveOffer(ContactId c, Offer o, RequestWriter r) throws DbException,
	IOException;

	/** Processes a subscription update from the given contact. */
	void receiveSubscriptionUpdate(ContactId c, SubscriptionUpdate s)
	throws DbException;

	/** Processes a transport update from the given contact. */
	void receiveTransportUpdate(ContactId c, TransportUpdate t)
	throws DbException;

	/** Removes a contact (and all associated state) from the database. */
	void removeContact(ContactId c) throws DbException;

	/**
	 * Sets the connection reordering window for the given contact and
	 * transport.
	 */
	void setConnectionWindow(ContactId c, TransportId t, ConnectionWindow w)
	throws DbException;

	/** Records the user's rating for the given author. */
	void setRating(AuthorId a, Rating r) throws DbException;

	/** Records the given messages as having been seen by the given contact. */
	void setSeen(ContactId c, Collection<MessageId> seen) throws DbException;

	/**
	 * Sets the configuration for the given transport, replacing any existing
	 * configuration for that transport.
	 */
	void setTransportConfig(TransportId t, Map<String, String> config)
	throws DbException;

	/**
	 * Sets the transport properties for the given transport, replacing any
	 * existing properties for that transport.
	 */
	void setTransportProperties(TransportId t, Map<String, String> properties)
	throws DbException;

	/**
	 * Makes the given group visible to the given set of contacts and invisible
	 * to any other contacts.
	 */
	void setVisibility(GroupId g, Collection<ContactId> visible)
	throws DbException;

	/** Subscribes to the given group. */
	void subscribe(Group g) throws DbException;

	/**
	 * Unsubscribes from the given group. Any messages belonging to the group
	 * are deleted from the database.
	 */
	void unsubscribe(GroupId g) throws DbException;
}
