package net.sf.briar.api.plugins;

import net.sf.briar.api.ContactId;

/**
 * An interface for receiving connections created by a duplex transport plugin.
 */
public interface DuplexPluginCallback extends PluginCallback {

	void incomingConnectionCreated(DuplexTransportConnection d);

	void outgoingConnectionCreated(ContactId c, DuplexTransportConnection d);
}
