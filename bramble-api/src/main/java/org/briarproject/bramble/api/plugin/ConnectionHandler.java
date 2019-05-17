package org.briarproject.bramble.api.plugin;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

/**
 * An interface for handling connections created by transport plugins.
 */
@NotNullByDefault
public interface ConnectionHandler {

	void handleConnection(DuplexTransportConnection c);

	void handleReader(TransportConnectionReader r);

	void handleWriter(TransportConnectionWriter w);
}
