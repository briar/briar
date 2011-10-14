package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;

public interface ConnectionDispatcher {

	void dispatchReader(TransportId t, BatchTransportReader r);

	void dispatchWriter(TransportId t, ContactId c,
			BatchTransportWriter w);

	void dispatchIncomingConnection(TransportId t, StreamTransportConnection s);

	void dispatchOutgoingConnection(TransportId t, ContactId c,
			StreamTransportConnection s);
}
