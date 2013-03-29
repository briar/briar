package net.sf.briar.api.messaging.duplex;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.transport.ConnectionContext;

public interface DuplexConnectionFactory {

	void createIncomingConnection(ConnectionContext ctx,
			DuplexTransportConnection d);

	void createOutgoingConnection(ContactId c, TransportId t,
			DuplexTransportConnection d);
}
