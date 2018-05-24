package org.briarproject.bramble.api.plugin;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.properties.TransportProperties;

import java.util.Map;

@NotNullByDefault
public interface Plugin {

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
	 * Returns true if the plugin is running.
	 */
	boolean isRunning();

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
	 * Attempts to establish connections to the given contacts, passing any
	 * created connections to the callback.
	 */
	void poll(Map<ContactId, TransportProperties> contacts);
}
