package org.briarproject.bramble.api.rendezvous;

import org.briarproject.bramble.api.properties.TransportProperties;

import java.io.Closeable;
import java.io.IOException;

/**
 * An interface for making and accepting rendezvous connections with a pending
 * contact over a given transport.
 */
public interface RendezvousEndpoint extends Closeable {

	/**
	 * Returns a set of transport properties for connecting to the pending
	 * contact.
	 */
	TransportProperties getRemoteTransportProperties();

	/**
	 * Closes the handler and releases any resources held by it, such as
	 * network sockets.
	 */
	@Override
	void close() throws IOException;
}
