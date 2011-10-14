package net.sf.briar.api.transport.batch;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.transport.BatchTransportReader;
import net.sf.briar.api.transport.BatchTransportWriter;

public interface BatchConnectionFactory {

	Runnable createIncomingConnection(ContactId c, BatchTransportReader r,
			byte[] encryptedIv);

	Runnable createOutgoingConnection(TransportId t, ContactId c,
			BatchTransportWriter w);
}
