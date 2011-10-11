package net.sf.briar.api.plugins;

import java.io.IOException;
import java.util.Map;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;

public interface TransportPlugin {

	/** Returns the plugin's transport identifier. */
	TransportId getId();

	/** Starts the plugin. */
	void start(TransportProperties localProperties,
			Map<ContactId, TransportProperties> remoteProperties,
			TransportConfig config) throws IOException;

	/** Stops the plugin. */
	void stop() throws IOException;

	/** Updates the plugin's local transport properties. */
	void setLocalProperties(TransportProperties p);

	/** Updates the plugin's transport properties for the given contact. */
	void setRemoteProperties(ContactId c, TransportProperties p);

	/** Updates the plugin's configuration properties. */
	void setConfig(TransportConfig c);

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
