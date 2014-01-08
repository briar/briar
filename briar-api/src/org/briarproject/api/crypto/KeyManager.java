package org.briarproject.api.crypto;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.transport.ConnectionContext;
import org.briarproject.api.transport.Endpoint;

public interface KeyManager extends Service {

	/**
	 * Returns a connection context for connecting to the given contact over
	 * the given transport, or null if an error occurs or the contact does not
	 * support the transport.
	 */
	ConnectionContext getConnectionContext(ContactId c, TransportId t);

	/**
	 * Called whenever an endpoint has been added. The initial secret
	 * is erased before returning.
	 */
	void endpointAdded(Endpoint ep, long maxLatency, byte[] initialSecret);
}
