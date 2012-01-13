package net.sf.briar.api.plugins.duplex;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.plugins.PluginCallback;

/**
 * An interface for handling connections created by a duplex transport plugin.
 */
public interface DuplexPluginCallback extends PluginCallback {

	void incomingConnectionCreated(DuplexTransportConnection d);

	void outgoingConnectionCreated(ContactId c, DuplexTransportConnection d);
}
