package org.briarproject.bramble.api.plugin;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.properties.TransportProperties;

/**
 * An interface for handling peers discovered by transport plugins.
 */
@NotNullByDefault
public interface DiscoveryHandler {

	/**
	 * Handles a peer discovered by a transport plugin.
	 *
	 * @param p A set of properties describing the discovered peer.
	 */
	void handleDevice(TransportProperties p);
}
