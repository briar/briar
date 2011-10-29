package net.sf.briar.api.plugins;

import java.io.IOException;

import net.sf.briar.api.TransportId;

public interface Plugin {

	/** Returns the plugin's transport identifier. */
	TransportId getId();

	/** Starts the plugin. */
	void start() throws IOException;

	/** Stops the plugin. */
	void stop() throws IOException;

	/**
	 * Returns true if the plugin's poll() method should be called
	 * periodically to attempt to establish connections.
	 */
	boolean shouldPoll();

	/**
	 * Returns the desired interval in milliseconds between calls to the
	 * plugin's poll() method.
	 */
	long getPollingInterval();

	/**
	 * Attempts to establish connections using the current transport and
	 * configuration properties, and passes any created connections to the
	 * callback.
	 */
	void poll();

	/** Returns true if the plugin supports exchanging invitations. */
	boolean supportsInvitations();
}
