package org.briarproject.api.plugins;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;

public interface ConnectionDispatcher {

	void dispatchIncomingConnection(TransportId t, TransportConnectionReader r);

	void dispatchIncomingConnection(TransportId t, DuplexTransportConnection d);

	void dispatchOutgoingConnection(ContactId c, TransportId t,
			TransportConnectionWriter w);

	void dispatchOutgoingConnection(ContactId c, TransportId t,
			DuplexTransportConnection d);
}
