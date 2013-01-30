package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.messaging.TransportId;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.plugins.simplex.SimplexTransportReader;
import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;

public interface ConnectionDispatcher {

	void dispatchReader(TransportId t, SimplexTransportReader r);

	void dispatchWriter(ContactId c, TransportId t,
			SimplexTransportWriter w);

	void dispatchIncomingConnection(TransportId t, DuplexTransportConnection d);

	void dispatchOutgoingConnection(ContactId c, TransportId t,
			DuplexTransportConnection d);
}
