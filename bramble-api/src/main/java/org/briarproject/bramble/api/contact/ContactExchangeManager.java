package org.briarproject.bramble.api.contact;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.ContactExistsException;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

import java.io.IOException;

@NotNullByDefault
public interface ContactExchangeManager {

	/**
	 * Exchanges contact information with a remote peer.
	 *
	 * @param alice Whether the local peer takes the role of Alice
	 * @return The newly added contact
	 * @throws ContactExistsException If the contact already exists
	 */
	Contact exchangeContacts(TransportId t, DuplexTransportConnection conn,
			SecretKey masterKey, boolean alice) throws IOException, DbException;
}
