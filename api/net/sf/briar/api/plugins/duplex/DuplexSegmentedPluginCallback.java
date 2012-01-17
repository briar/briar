package net.sf.briar.api.plugins.duplex;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.plugins.PluginCallback;

/**
 * An interface for handling connections created by a duplex segmented
 * transport plugin.
 */
public interface DuplexSegmentedPluginCallback extends PluginCallback {

	void incomingConnectionCreated(DuplexSegmentedTransportConnection d);

	void outgoingConnectionCreated(ContactId c,
			DuplexSegmentedTransportConnection d);

}
