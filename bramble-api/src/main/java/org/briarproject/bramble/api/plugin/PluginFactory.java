package org.briarproject.bramble.api.plugin;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

@NotNullByDefault
public interface PluginFactory<P extends Plugin> {

	/**
	 * Returns the plugin's transport identifier.
	 */
	TransportId getId();

	/**
	 * Returns the maximum latency of the transport in milliseconds.
	 */
	long getMaxLatency();

	/**
	 * Creates and returns a plugin, or null if no plugin can be created.
	 */
	@Nullable
	P createPlugin(PluginCallback callback);
}
