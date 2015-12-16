package org.briarproject.api.plugins;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;

public interface ConnectionManager {

	void manageIncomingConnection(TransportId t, TransportConnectionReader r);

	void manageIncomingConnection(TransportId t, DuplexTransportConnection d);

	void manageOutgoingConnection(ContactId c, TransportId t,
			TransportConnectionWriter w);

	void manageOutgoingConnection(ContactId c, TransportId t,
			DuplexTransportConnection d);
}
