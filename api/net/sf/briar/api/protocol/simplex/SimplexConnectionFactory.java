package net.sf.briar.api.protocol.simplex;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.plugins.simplex.SimplexTransportReader;
import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionContext;

public interface SimplexConnectionFactory {

	void createIncomingConnection(ConnectionContext ctx, TransportId t,
			SimplexTransportReader r, byte[] tag);

	void createOutgoingConnection(ContactId c, TransportId t, TransportIndex i,
			SimplexTransportWriter w);
}
