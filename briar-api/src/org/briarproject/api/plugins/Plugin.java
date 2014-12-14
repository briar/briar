package org.briarproject.api.plugins;

import java.io.IOException;
import java.util.Collection;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;

public interface Plugin {

	/** Returns the plugin's transport identifier. */
	TransportId getId();

	/** Returns the transport's maximum frame length in bytes. */
	int getMaxFrameLength();

	/** Returns the transport's maximum latency in milliseconds. */
	int getMaxLatency();

	/** Returns the transport's maximum idle time in milliseconds. */
	int getMaxIdleTime();

	/** Starts the plugin and returns true if it started successfully. */
	boolean start() throws IOException;

	/** Stops the plugin. */
	void stop() throws IOException;

	/** Returns true if the plugin is running. */
	boolean isRunning();

	/**
	 * Returns true if the plugin's {@link #poll(Collection)} method should be
	 * called periodically to attempt to establish connections.
	 */
	boolean shouldPoll();

	/**
	 * Returns the desired interval in milliseconds between calls to the
	 * plugin's {@link #poll(Collection)} method.
	 */
	int getPollingInterval();

	/**
	 * Attempts to establish connections to contacts, passing any created
	 * connections to the callback. To avoid creating redundant connections,
	 * the plugin may exclude the given contacts from polling.
	 */
	void poll(Collection<ContactId> connected);
}
