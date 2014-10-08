package org.briarproject.api.messaging.duplex;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.transport.StreamContext;

public interface DuplexConnectionFactory {

	void createIncomingConnection(StreamContext ctx,
			DuplexTransportConnection d);

	void createOutgoingConnection(ContactId c, TransportId t,
			DuplexTransportConnection d);
}
