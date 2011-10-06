package net.sf.briar.api.transport.stream;

import java.io.IOException;
import java.util.Map;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.transport.InvalidConfigException;
import net.sf.briar.api.transport.InvalidPropertiesException;
import net.sf.briar.api.transport.TransportPlugin;

/**
 * An interface for transport plugins that support bidirectional, reliable,
 * ordered, timely delivery of data.
 */
public interface StreamTransportPlugin extends TransportPlugin {

	/**
	 * Starts the plugin. Any connections that are later initiated by contacts
	 * or established through polling will be passed to the given callback.
	 */
	void start(Map<String, String> localProperties,
			Map<ContactId, Map<String, String>> remoteProperties,
			Map<String, String> config, StreamTransportCallback c)
	throws InvalidPropertiesException, InvalidConfigException, IOException;

	/**
	 * Attempts to create and return a StreamTransportConnection to the given
	 * contact using the current transport and configuration properties.
	 * Returns null if a connection could not be created.
	 */
	StreamTransportConnection createConnection(ContactId c);
}
