package net.sf.briar.api.protocol.duplex;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionContext;

public interface DuplexConnectionFactory {

	void createIncomingConnection(ConnectionContext ctx, TransportId t,
			DuplexTransportConnection d);

	void createOutgoingConnection(ContactId c, TransportId t, TransportIndex i,
			DuplexTransportConnection d);
}
