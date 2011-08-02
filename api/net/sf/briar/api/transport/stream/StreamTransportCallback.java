package net.sf.briar.api.transport.stream;

/**
 * An interface for receiving connections created by a stream-mode transport
 * plugin.
 */
public interface StreamTransportCallback {

	void connectionCreated(StreamTransportConnection c);
}
