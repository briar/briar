package net.sf.briar.api.transport.stream;

import net.sf.briar.api.transport.TransportCallback;

/**
 * An interface for receiving connections created by a stream-mode transport
 * plugin.
 */
public interface StreamTransportCallback extends TransportCallback {

	void connectionCreated(StreamTransportConnection c);
}
