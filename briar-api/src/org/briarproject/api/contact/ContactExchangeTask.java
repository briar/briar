package org.briarproject.api.contact;

import org.briarproject.api.TransportId;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;

/**
 * A task for conducting a contact information exchange with a remote peer.
 */
public interface ContactExchangeTask {

	/**
	 * Exchange contact information with a remote peer.
	 */
	void startExchange(ContactExchangeListener listener,
			LocalAuthor localAuthor, SecretKey masterSecret,
			DuplexTransportConnection conn, TransportId transportId,
			boolean alice);
}
