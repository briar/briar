package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;

public interface BatchConnectionFactory {

	void createIncomingConnection(ContactId c, BatchTransportReader r,
			byte[] encryptedIv);

	void createOutgoingConnection(TransportId t, ContactId c,
			BatchTransportWriter w);
}
