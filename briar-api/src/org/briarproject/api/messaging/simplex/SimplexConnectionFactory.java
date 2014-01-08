package org.briarproject.api.messaging.simplex;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.plugins.simplex.SimplexTransportReader;
import org.briarproject.api.plugins.simplex.SimplexTransportWriter;
import org.briarproject.api.transport.ConnectionContext;

public interface SimplexConnectionFactory {

	void createIncomingConnection(ConnectionContext ctx,
			SimplexTransportReader r);

	void createOutgoingConnection(ContactId c, TransportId t,
			SimplexTransportWriter w);
}
