package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportIndex;

public interface BatchConnectionFactory {

	void createIncomingConnection(ConnectionContext ctx,
			BatchTransportReader r, byte[] tag);

	void createOutgoingConnection(ContactId c, TransportIndex i,
			BatchTransportWriter w);
}
