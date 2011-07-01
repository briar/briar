package net.sf.briar.api.db;

import java.util.Set;

import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Bundle;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;

/**
 * Encapsulates the database implementation and exposes high-level operations
 * to other components.
 */
public interface DatabaseComponent {

	static final long MEGABYTES = 1024L * 1024L;
	static final long GIGABYTES = 1024L * MEGABYTES;

	// FIXME: Some of these should be configurable
	static final long MAX_DB_SIZE = 2L * GIGABYTES;
	static final long MIN_FREE_SPACE = 300L * MEGABYTES;
	static final long CRITICAL_FREE_SPACE = 100L * MEGABYTES;
	static final long MAX_BYTES_BETWEEN_SPACE_CHECKS = 5L * MEGABYTES;
	static final long MAX_MS_BETWEEN_SPACE_CHECKS = 60L * 1000L; // 1 min
	static final long BYTES_PER_SWEEP = 5L * MEGABYTES;
	static final int CLEANER_SLEEP_MS = 1000; // 1 sec
	static final int RETRANSMIT_THRESHOLD = 3;

	/** Waits for any open transactions to finish and closes the database. */
	void close() throws DbException;

	/** Adds a new contact to the database. */
	void addContact(ContactId c) throws DbException;

	/** Adds a locally generated message to the database. */
	void addLocallyGeneratedMessage(Message m) throws DbException;

	/**
	 * Generates a bundle of acknowledgements, subscriptions, and batches of
	 * messages for the given contact.
	 */
	void generateBundle(ContactId c, Bundle b) throws DbException;

	/** Returns the user's rating for the given author. */
	Rating getRating(AuthorId a) throws DbException;

	/** Returns the set of groups to which the user subscribes. */
	Set<GroupId> getSubscriptions() throws DbException;

	/**
	 * Processes a bundle of acknowledgements, subscriptions, and batches of
	 * messages received from the given contact. Some or all of the messages
	 * in the bundle may be stored.
	 */
	void receiveBundle(ContactId c, Bundle b) throws DbException;

	/** Removes a contact (and all associated state) from the database. */
	void removeContact(ContactId c) throws DbException;

	/** Records the user's rating for the given author. */
	void setRating(AuthorId a, Rating r) throws DbException;

	/** Subscribes to the given group. */
	void subscribe(GroupId g) throws DbException;

	/**
	 * Unsubscribes from the given group. Any messages belonging to the group
	 * are deleted from the database.
	 */
	void unsubscribe(GroupId g) throws DbException;
}
