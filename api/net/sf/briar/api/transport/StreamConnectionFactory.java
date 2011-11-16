package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportIndex;

public interface StreamConnectionFactory {

	void createIncomingConnection(ConnectionContext ctx,
			StreamTransportConnection s, byte[] encryptedIv);

	void createOutgoingConnection(ContactId c, TransportIndex i,
			StreamTransportConnection s);
}
