package net.sf.briar.api.crypto;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.lifecycle.Service;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.Endpoint;

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
