package net.sf.briar.api.transport.batch;

import java.io.IOException;
import java.util.Map;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.transport.InvalidConfigException;
import net.sf.briar.api.transport.InvalidTransportException;

/**
 * An interface for transport plugins that do not support bidirectional,
 * reliable, ordered, timely delivery of data.
 */
public interface BatchTransportPlugin {

	/** Returns the plugin's transport identifier. */
	TransportId getId();

	/**
	 * Starts the plugin. Any connections that are later initiated by contacts
	 * or established through polling will be passed to the given callback.
	 */
	void start(Map<String, String> localProperties,
			Map<ContactId, Map<String, String>> remoteProperties,
			Map<String, String> config, BatchTransportCallback c)
	throws InvalidTransportException, InvalidConfigException, IOException;

	/**
	 * Stops the plugin. No further connections will be passed to the callback
	 * after this method has returned.
	 */
	void stop() throws IOException;

	/** Updates the plugin's local transport properties. */
	void setLocalProperties(Map<String, String> properties)
	throws InvalidTransportException;

	/** Updates the plugin's transport properties for the given contact. */
	void setRemoteProperties(ContactId c, Map<String, String> properties)
	throws InvalidTransportException;

	/** Updates the plugin's configuration properties. */
	void setConfig(Map<String, String> config) throws InvalidConfigException;

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
	 * Attempts to establish incoming and/or outgoing connections using the
	 * current transport and configuration properties, and passes any created
	 * readers and/or writers to the callback.
	 */
	void poll();

	/**
	 * Attempts to create and return a BatchTransportReader for the given
	 * contact using the current transport and configuration properties.
	 * Returns null if a reader could not be created.
	 */
	BatchTransportReader createReader(ContactId c);

	/**
	 * Attempts to create and return a BatchTransportWriter for the given
	 * contact using the current transport and configuration properties.
	 * Returns null if a writer could not be created.
	 */
	BatchTransportWriter createWriter(ContactId c);
}
