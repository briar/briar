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
		 * The plugin is being enabled and can't yet make or receive
		 * connections.
		 */
		ENABLING,

		/**
		 * The plugin is enabled and can make or receive connections.
		 */
		ACTIVE,

		/**
		 * The plugin is enabled but can't make or receive connections
		 */
		INACTIVE
	}

	/**
	 * Reason code returned by {@link #getReasonDisabled()} ()} to indicate
	 * that the plugin is disabled because it has not been started or has been
	 * stopped.
	 */
	int REASON_STARTING_STOPPING = 0;

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
	 * Returns an integer code indicating why the plugin is
	 * {@link State#DISABLED disabled}, or -1 if the plugin is not disabled.
	 * <p>
	 * The codes used are plugin-specific, except the generic code
	 * {@link #REASON_STARTING_STOPPING}, which may be used by
	 * any plugin.
	 */
	int getReasonDisabled();

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
