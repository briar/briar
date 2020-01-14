package org.briarproject.bramble.api.plugin;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.properties.TransportProperties;

import java.util.Collection;

@NotNullByDefault
public interface Plugin {

	enum State {

		/**
		 * The plugin has not been started, has been stopped, or is disabled by
		 * settings.
		 */
		DISABLED,

		/**
		 * The plugin has been started, has not been stopped, is enabled by
		 * settings, but can't yet tell whether it can make or receive
		 * connections.
		 */
		ENABLING,

		/**
		 * The plugin has been started, has not been stopped, is enabled by
		 * settings, and can make or receive connections.
		 */
		AVAILABLE,

		/**
		 * The plugin has been started, has not been stopped, is enabled by
		 * settings, but can't make or receive connections
		 */
		UNAVAILABLE
	}

	/**
	 * Returns the plugin's transport identifier.
	 */
	TransportId getId();

	/**
	 * Returns the transport's maximum latency in milliseconds.
	 */
	int getMaxLatency();

	/**
	 * Returns the transport's maximum idle time in milliseconds.
	 */
	int getMaxIdleTime();

	/**
	 * Starts the plugin.
	 */
	void start() throws PluginException;

	/**
	 * Stops the plugin.
	 */
	void stop() throws PluginException;

	/**
	 * Returns the current state of the plugin.
	 */
	State getState();

	/**
	 * Returns true if the plugin should be polled periodically to attempt to
	 * establish connections.
	 */
	boolean shouldPoll();

	/**
	 * Returns the desired interval in milliseconds between polling attempts.
	 */
	int getPollingInterval();

	/**
	 * Attempts to create connections using the given transport properties,
	 * passing any created connections to the corresponding handlers.
	 */
	void poll(Collection<Pair<TransportProperties, ConnectionHandler>>
			properties);
}
