package org.briarproject.bramble.api.contact;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.transport.StreamWriter;

import java.io.IOException;
import java.io.InputStream;

@NotNullByDefault
public interface HandshakeManager {

	/**
	 * Handshakes and exchanges pseudonyms with the given pending contact,
	 * converts the pending contact to a contact and returns the contact.
	 *
	 * @param in An incoming stream for the handshake, which must be secured in
	 * handshake mode
	 * @param out An outgoing stream for the handshake, which must be secured
	 * in handshake mode
	 * @param conn The connection to use for the handshake and contact exchange
	 */
	Contact handshakeAndAddContact(PendingContactId p,
			InputStream in, StreamWriter out, DuplexTransportConnection conn)
			throws DbException, IOException;
}
