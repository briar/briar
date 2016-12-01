package org.briarproject.bramble.api.contact;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

/**
 * A task for conducting a contact information exchange with a remote peer.
 */
@NotNullByDefault
public interface ContactExchangeTask {

	/**
	 * Exchanges contact information with a remote peer.
	 */
	void startExchange(ContactExchangeListener listener,
			LocalAuthor localAuthor, SecretKey masterSecret,
			DuplexTransportConnection conn, TransportId transportId,
			boolean alice);
}
