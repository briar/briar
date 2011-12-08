package net.sf.briar.api.protocol.stream;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.StreamTransportConnection;

public interface StreamConnectionFactory {

	void createIncomingConnection(ConnectionContext ctx,
			StreamTransportConnection s, byte[] tag);

	void createOutgoingConnection(ContactId c, TransportIndex i,
			StreamTransportConnection s);
}
