package org.briarproject.api.transport;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;

/**
 * Responsible for managing transport keys and recognising the pseudo-random
 * tags of incoming streams.
 */
public interface KeyManager {

	/**
	 * Informs the key manager that a new contact has been added. Derives and
	 * stores transport keys for communicating with the contact.
	 * {@link StreamContext StreamContexts} for the contact can be created
	 * after this method has returned.
	 */
	void addContact(Transaction txn, ContactId c, SecretKey master,
			long timestamp, boolean alice) throws DbException;

	/**
	 * Returns a {@link StreamContext} for sending a stream to the given
	 * contact over the given transport, or null if an error occurs or the
	 * contact does not support the transport.
	 */
	StreamContext getStreamContext(ContactId c, TransportId t);

	/**
	 * Looks up the given tag and returns a {@link StreamContext} for reading
	 * from the corresponding stream, or null if an error occurs or the tag was
	 * unexpected.
	 */
	StreamContext getStreamContext(TransportId t, byte[] tag);
}
