package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;

public interface ConnectionDispatcher {

	void dispatchReader(TransportId t, BatchTransportReader r);

	void dispatchWriter(TransportIndex i, ContactId c, BatchTransportWriter w);

	void dispatchIncomingConnection(TransportId t, StreamTransportConnection s);

	void dispatchOutgoingConnection(TransportIndex i, ContactId c,
			StreamTransportConnection s);
}
