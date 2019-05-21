package org.briarproject.bramble.api.plugin;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;

/**
 * An interface for handling connections created by transport plugins.
 */
@NotNullByDefault
public interface ConnectionHandler {

	/**
	 * Handles a connection created by a {@link DuplexPlugin}.
	 */
	void handleConnection(DuplexTransportConnection c);

	/**
	 * Handles a reader created by a {@link SimplexPlugin}.
	 */
	void handleReader(TransportConnectionReader r);

	/**
	 * Handles a writer created by a {@link SimplexPlugin}.
	 */
	void handleWriter(TransportConnectionWriter w);
}
