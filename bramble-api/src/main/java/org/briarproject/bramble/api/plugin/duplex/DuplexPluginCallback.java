package org.briarproject.bramble.api.plugin.duplex;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginCallback;

/**
 * An interface for handling connections created by a duplex transport plugin.
 */
@NotNullByDefault
public interface DuplexPluginCallback extends PluginCallback {

	void incomingConnectionCreated(DuplexTransportConnection d);

	void outgoingConnectionCreated(ContactId c, DuplexTransportConnection d);
}
