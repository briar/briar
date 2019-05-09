package org.briarproject.bramble.api.contact;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

@NotNullByDefault
public interface ContactExchangeManager {

	/**
	 * Exchanges contact information with a remote peer.
	 */
	void exchangeContacts(TransportId t, DuplexTransportConnection conn,
			SecretKey masterKey, boolean alice);
}
