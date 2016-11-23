package org.briarproject.bramble.api.plugin.duplex;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;

import javax.annotation.Nullable;

/**
 * Factory for creating a plugin for a duplex transport.
 */
@NotNullByDefault
public interface DuplexPluginFactory {

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
	DuplexPlugin createPlugin(DuplexPluginCallback callback);
}
