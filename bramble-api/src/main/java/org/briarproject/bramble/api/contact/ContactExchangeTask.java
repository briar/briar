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
	 * The current version of the contact exchange protocol
	 */
	int PROTOCOL_VERSION = 0;

	/**
	 * Label for deriving Alice's header key from the master secret.
	 */
	String ALICE_KEY_LABEL =
			"org.briarproject.bramble.contact/ALICE_HEADER_KEY";

	/**
	 * Label for deriving Bob's header key from the master secret.
	 */
	String BOB_KEY_LABEL = "org.briarproject.bramble.contact/BOB_HEADER_KEY";

	/**
	 * Label for deriving Alice's key binding nonce from the master secret.
	 */
	String ALICE_NONCE_LABEL = "org.briarproject.bramble.contact/ALICE_NONCE";

	/**
	 * Label for deriving Bob's key binding nonce from the master secret.
	 */
	String BOB_NONCE_LABEL = "org.briarproject.bramble.contact/BOB_NONCE";

	/**
	 * Exchanges contact information with a remote peer.
	 */
	void startExchange(ContactExchangeListener listener,
			LocalAuthor localAuthor, SecretKey masterSecret,
			DuplexTransportConnection conn, TransportId transportId,
			boolean alice);
}
