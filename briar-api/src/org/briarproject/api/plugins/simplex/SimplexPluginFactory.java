package org.briarproject.api.plugins.simplex;

import org.briarproject.api.TransportId;

/**
 * Factory for creating a plugin for a simplex transport.
 */
public interface SimplexPluginFactory {

	/**
	 * Returns the plugin's transport identifier.
	 */
	TransportId getId();

	/**
	 * Returns the maximum latency of the transport in milliseconds.
	 */
	int getMaxLatency();

	/**
	 * Creates and returns a plugin, or null if no plugin can be created.
	 */
	SimplexPlugin createPlugin(SimplexPluginCallback callback);
}
