package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.plugin.TransportId;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * Responsible for managing transport keys and recognising the pseudo-random
 * tags of incoming streams.
 */
public interface KeyManager {

	/**
	 * Informs the key manager that a new contact has been added. Derives and
	 * stores a set of rotation mode transport keys for communicating with the
	 * contact over each transport and returns the key set IDs.
	 * <p/>
	 * {@link StreamContext StreamContexts} for the contact can be created
	 * after this method has returned.
	 *
	 * @param alice True if the local party is Alice
	 * @param active Whether the derived keys can be used for outgoing streams
	 */
	Map<TransportId, KeySetId> addContact(Transaction txn, ContactId c,
			SecretKey rootKey, long timestamp, boolean alice, boolean active)
			throws DbException;

	/**
	 * Informs the key manager that a new contact has been added. Derives and
	 * stores a set of handshake mode transport keys for communicating with the
	 * contact over each transport and returns the key set IDs.
	 * <p/>
	 * {@link StreamContext StreamContexts} for the contact can be created
	 * after this method has returned.
	 *
	 * @param alice True if the local party is Alice
	 */
	Map<TransportId, KeySetId> addContact(Transaction txn, ContactId c,
			SecretKey rootKey, boolean alice) throws DbException;

	/**
	 * Informs the key manager that a new pending contact has been added.
	 * Derives and stores a set of handshake mode transport keys for
	 * communicating with the pending contact over each transport and returns
	 * the key set IDs.
	 * <p/>
	 * {@link StreamContext StreamContexts} for the pending contact can be
	 * created after this method has returned.
	 *
	 * @param alice True if the local party is Alice
	 */
	Map<TransportId, KeySetId> addPendingContact(Transaction txn,
			PendingContactId p, SecretKey rootKey, boolean alice)
			throws DbException;

	/**
	 * Marks the given transport keys as usable for outgoing streams.
	 */
	void activateKeys(Transaction txn, Map<TransportId, KeySetId> keys)
			throws DbException;

	/**
	 * Returns true if we have keys that can be used for outgoing streams to
	 * the given contact over the given transport.
	 */
	boolean canSendOutgoingStreams(ContactId c, TransportId t);

	/**
	 * Returns true if we have keys that can be used for outgoing streams to
	 * the given pending contact over the given transport.
	 */
	boolean canSendOutgoingStreams(PendingContactId p, TransportId t);

	/**
	 * Returns a {@link StreamContext} for sending a stream to the given
	 * contact over the given transport, or null if an error occurs.
	 */
	@Nullable
	StreamContext getStreamContext(ContactId c, TransportId t)
			throws DbException;

	/**
	 * Returns a {@link StreamContext} for sending a stream to the given
	 * pending contact over the given transport, or null if an error occurs.
	 */
	@Nullable
	StreamContext getStreamContext(PendingContactId p, TransportId t)
			throws DbException;

	/**
	 * Looks up the given tag and returns a {@link StreamContext} for reading
	 * from the corresponding stream, or null if an error occurs or the tag was
	 * unexpected.
	 */
	@Nullable
	StreamContext getStreamContext(TransportId t, byte[] tag)
			throws DbException;
}
