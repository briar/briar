package org.briarproject.api.plugins.duplex;

import org.briarproject.api.ContactId;
import org.briarproject.api.plugins.PluginCallback;

/**
 * An interface for handling connections created by a duplex transport plugin.
 */
public interface DuplexPluginCallback extends PluginCallback {

	void incomingConnectionCreated(DuplexTransportConnection d);

	void outgoingConnectionCreated(ContactId c, DuplexTransportConnection d);
}
