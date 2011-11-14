package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportIndex;

public interface StreamConnectionFactory {

	void createIncomingConnection(TransportIndex i, ContactId c, 
			StreamTransportConnection s, byte[] encryptedIv);

	void createOutgoingConnection(TransportIndex i, ContactId c,
			StreamTransportConnection s);
}
