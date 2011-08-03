package net.sf.briar.api.transport.stream;

import java.util.Map;

/**
 * An interface for receiving connections created by a stream-mode transport
 * plugin.
 */
public interface StreamTransportCallback {

	void connectionCreated(StreamTransportConnection c);

	void setLocalTransports(Map<String, String> transports);

	void setConfig(Map<String, String> config);
}
