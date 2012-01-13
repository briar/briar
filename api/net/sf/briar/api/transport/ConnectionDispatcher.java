package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.plugins.simplex.SimplexTransportReader;
import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;

public interface ConnectionDispatcher {

	void dispatchReader(TransportId t, SimplexTransportReader r);

	void dispatchWriter(ContactId c, TransportId t, TransportIndex i,
			SimplexTransportWriter w);

	void dispatchIncomingConnection(TransportId t, DuplexTransportConnection d);

	void dispatchOutgoingConnection(ContactId c, TransportId t,
			TransportIndex i, DuplexTransportConnection d);
}
