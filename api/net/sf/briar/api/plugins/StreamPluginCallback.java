package net.sf.briar.api.plugins;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.transport.StreamTransportConnection;

/**
 * An interface for receiving connections created by a stream-mode transport
 * plugin.
 */
public interface StreamPluginCallback extends PluginCallback {

	void incomingConnectionCreated(StreamTransportConnection c);

	void outgoingConnectionCreated(ContactId contactId,
			StreamTransportConnection c);
}
