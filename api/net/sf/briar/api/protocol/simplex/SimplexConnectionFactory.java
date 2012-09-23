package net.sf.briar.api.protocol.simplex;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.plugins.simplex.SimplexTransportReader;
import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.transport.ConnectionContext;

public interface SimplexConnectionFactory {

	void createIncomingConnection(ConnectionContext ctx, SimplexTransportReader r);

	void createOutgoingConnection(ContactId c, TransportId t,
			SimplexTransportWriter w);
}
