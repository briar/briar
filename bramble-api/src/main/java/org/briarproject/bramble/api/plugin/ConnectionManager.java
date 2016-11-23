package org.briarproject.bramble.api.plugin;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

public interface ConnectionManager {

	void manageIncomingConnection(TransportId t, TransportConnectionReader r);

	void manageIncomingConnection(TransportId t, DuplexTransportConnection d);

	void manageOutgoingConnection(ContactId c, TransportId t,
			TransportConnectionWriter w);

	void manageOutgoingConnection(ContactId c, TransportId t,
			DuplexTransportConnection d);
}
