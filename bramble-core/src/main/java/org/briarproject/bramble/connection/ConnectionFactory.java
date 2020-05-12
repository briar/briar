package org.briarproject.bramble.connection;

import org.briarproject.bramble.api.connection.ConnectionManager;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

@NotNullByDefault
interface ConnectionFactory {

	Runnable createIncomingSimplexSyncConnection(TransportId t,
			TransportConnectionReader r);

	Runnable createIncomingDuplexSyncConnection(TransportId t,
			DuplexTransportConnection d);

	Runnable createIncomingHandshakeConnection(
			ConnectionManager connectionManager, PendingContactId p,
			TransportId t, DuplexTransportConnection d);

	Runnable createOutgoingSimplexSyncConnection(ContactId c, TransportId t,
			TransportConnectionWriter w);

	Runnable createOutgoingDuplexSyncConnection(ContactId c, TransportId t,
			DuplexTransportConnection d);

	Runnable createOutgoingHandshakeConnection(
			ConnectionManager connectionManager,
			PendingContactId p, TransportId t, DuplexTransportConnection d);
}
