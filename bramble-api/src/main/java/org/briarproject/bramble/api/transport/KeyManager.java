package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.contact.ContactId;
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
	 * stores a set of transport keys for communicating with the contact over
	 * each transport.
	 * <p/>
	 * {@link StreamContext StreamContexts} for the contact can be created
	 * after this method has returned.
	 */
	void addContact(Transaction txn, ContactId c, SecretKey master,
			long timestamp, boolean alice) throws DbException;

	/**
	 * Derives and stores a set of unbound transport keys for each transport
	 * and returns the key set IDs.
	 */
	Map<TransportId, KeySetId> addUnboundKeys(Transaction txn, SecretKey master,
			long timestamp, boolean alice) throws DbException;

	/**
	 * Binds the given transport keys to the given contact.
	 */
	void bindKeys(Transaction txn, ContactId c, Map<TransportId, KeySetId> keys)
			throws DbException;

	/**
	 * Returns a {@link StreamContext} for sending a stream to the given
	 * contact over the given transport, or null if an error occurs or the
	 * contact does not support the transport.
	 */
	@Nullable
	StreamContext getStreamContext(ContactId c, TransportId t)
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
