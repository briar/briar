package net.sf.briar.api.transport.stream;

import java.util.Map;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.transport.InvalidConfigException;
import net.sf.briar.api.transport.InvalidTransportException;

/**
 * An interface for transport plugins that support bidirectional, reliable,
 * ordered, timely delivery of data.
 */
public interface StreamTransportPlugin {

	/**
	 * Returns the plugin's name, which is used to distinguish its transport
	 * and configuration properties from those of other plugins.
	 */
	String getName();

	/**
	 * Starts the plugin. Any connections that are later initiated by contacts
	 * or established through polling will be passed to the given callback.
	 */
	void start(Map<String, String> localProperties,
			Map<ContactId, Map<String, String>> remoteProperties,
			Map<String, String> config, StreamTransportCallback c)
	throws InvalidTransportException, InvalidConfigException;

	/**
	 * Stops the plugin. No further connections will be passed to the callback
	 * after this method has returned.
	 */
	void stop();

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
	 * Returns the desired interval in seconds between calls to the plugin's
	 * poll() method.
	 */
	int getPollingInterval();

	/**
	 * Attempts to establish connections using the current transport and
	 * configuration properties, and passes any created connections to the
	 * callback.
	 */
	void poll();

	/**
	 * Attempts to create and return a StreamTransportConnection to the given
	 * contact using the current transport and configuration properties.
	 * Returns null if a connection could not be created.
	 */
	StreamTransportConnection createConnection(ContactId c);
}
