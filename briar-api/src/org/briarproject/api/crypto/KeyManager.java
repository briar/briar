package org.briarproject.api.crypto;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.transport.Endpoint;
import org.briarproject.api.transport.StreamContext;

public interface KeyManager extends Service {

	/**
	 * Returns a {@link org.briarproject.api.transport.StreamContext
	 * StreamContext} for sending data to the given contact over the given
	 * transport, or null if an error occurs or the contact does not support
	 * the transport.
	 */
	StreamContext getStreamContext(ContactId c, TransportId t);

	/** Called whenever an endpoint has been added. */
	void endpointAdded(Endpoint ep, int maxLatency, byte[] initialSecret);
}
