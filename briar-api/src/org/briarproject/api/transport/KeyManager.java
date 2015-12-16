package org.briarproject.api.transport;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.lifecycle.Service;

import java.util.Collection;

/**
 * Responsible for managing transport keys and recognising the pseudo-random
 * tags of incoming streams.
 */
public interface KeyManager extends Service {

	/**
	 * Informs the key manager that a new contact has been added.
	 * {@link StreamContext StreamContexts} for the contact can be created
	 * after this method has returned.
	 */
	void contactAdded(ContactId c, Collection<TransportKeys> keys);

	/**
	 * Returns a {@link StreamContext} for sending a stream to the given
	 * contact over the given transport, or null if an error occurs or the
	 * contact does not support the transport.
	 */
	StreamContext getStreamContext(ContactId c, TransportId t);

	/**
	 * Looks up the given tag and returns a {@link StreamContext} for reading
	 * from the corresponding stream if the tag was expected, or null if the
	 * tag was unexpected.
	 */
	StreamContext recogniseTag(TransportId t, byte[] tag) throws DbException;
}
