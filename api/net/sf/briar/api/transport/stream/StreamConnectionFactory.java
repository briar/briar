package net.sf.briar.api.transport.stream;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.transport.StreamTransportConnection;

public interface StreamConnectionFactory {

	Runnable[] createIncomingConnection(ContactId c,
			StreamTransportConnection s, byte[] encryptedIv);

	Runnable[] createOutgoingConnection(TransportId t, ContactId c,
			StreamTransportConnection s);
}
