package org.briarproject.bramble.api.plugin.simplex;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;

import javax.annotation.Nullable;

/**
 * Factory for creating a plugin for a simplex transport.
 */
@NotNullByDefault
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
	@Nullable
	SimplexPlugin createPlugin(SimplexPluginCallback callback);
}
