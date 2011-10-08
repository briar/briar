package net.sf.briar.api.transport;

import java.io.IOException;
import java.util.Map;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;

public interface TransportPlugin {

	/** Returns the plugin's transport identifier. */
	TransportId getId();

	/** Starts the plugin. */
	void start(Map<String, String> localProperties,
			Map<ContactId, Map<String, String>> remoteProperties,
			Map<String, String> config) throws IOException;

	/**
	 * Stops the plugin. No further connections will be passed to the callback
	 * after this method has returned.
	 */
	void stop() throws IOException;

	/** Updates the plugin's local transport properties. */
	void setLocalProperties(Map<String, String> properties);

	/** Updates the plugin's transport properties for the given contact. */
	void setRemoteProperties(ContactId c, Map<String, String> properties);

	/** Updates the plugin's configuration properties. */
	void setConfig(Map<String, String> config);

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
}
