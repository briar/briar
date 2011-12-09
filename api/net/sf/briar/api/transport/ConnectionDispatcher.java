package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;

public interface ConnectionDispatcher {

	void dispatchReader(TransportId t, BatchTransportReader r);

	void dispatchWriter(ContactId c, TransportId t, TransportIndex i,
			BatchTransportWriter w);

	void dispatchIncomingConnection(TransportId t, StreamTransportConnection s);

	void dispatchOutgoingConnection(ContactId c, TransportId t,
			TransportIndex i, StreamTransportConnection s);
}
