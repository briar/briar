package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;

public interface StreamConnectionFactory {

	void createIncomingConnection(TransportId t, ContactId c, 
			StreamTransportConnection s, byte[] encryptedIv);

	void createOutgoingConnection(TransportId t, ContactId c,
			StreamTransportConnection s);
}
