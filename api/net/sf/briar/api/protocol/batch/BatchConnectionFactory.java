package net.sf.briar.api.protocol.batch;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.BatchTransportReader;
import net.sf.briar.api.transport.BatchTransportWriter;
import net.sf.briar.api.transport.ConnectionContext;

public interface BatchConnectionFactory {

	void createIncomingConnection(ConnectionContext ctx,
			BatchTransportReader r, byte[] tag);

	void createOutgoingConnection(ContactId c, TransportIndex i,
			BatchTransportWriter w);
}
