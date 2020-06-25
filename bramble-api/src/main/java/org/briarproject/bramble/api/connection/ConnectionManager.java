package org.briarproject.bramble.api.connection;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

@NotNullByDefault
public interface ConnectionManager {

	/**
	 * Manages an incoming connection from a contact over a simplex transport.
	 */
	void manageIncomingConnection(TransportId t, TransportConnectionReader r);

	/**
	 * Manages an incoming connection from a contact over a duplex transport.
	 */
	void manageIncomingConnection(TransportId t, DuplexTransportConnection d);

	/**
	 * Manages an incoming handshake connection from a pending contact over a
	 * duplex transport.
	 */
	void manageIncomingConnection(PendingContactId p, TransportId t,
			DuplexTransportConnection d);

	/**
	 * Manages an outgoing connection to a contact over a simplex transport.
	 */
	void manageOutgoingConnection(ContactId c, TransportId t,
			TransportConnectionWriter w);

	/**
	 * Manages an outgoing connection to a contact over a duplex transport.
	 */
	void manageOutgoingConnection(ContactId c, TransportId t,
			DuplexTransportConnection d);

	/**
	 * Manages an outgoing handshake connection to a pending contact over a
	 * duplex transport.
	 */
	void manageOutgoingConnection(PendingContactId p, TransportId t,
			DuplexTransportConnection d);
}
