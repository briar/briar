package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportIndex;

public interface BatchConnectionFactory {

	void createIncomingConnection(TransportIndex i, ContactId c,
			BatchTransportReader r, byte[] encryptedIv);

	void createOutgoingConnection(TransportIndex i, ContactId c,
			BatchTransportWriter w);
}
