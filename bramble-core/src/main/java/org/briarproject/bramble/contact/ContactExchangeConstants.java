package org.briarproject.bramble.contact;

interface ContactExchangeConstants {

	/**
	 * The current version of the contact exchange protocol.
	 */
	byte PROTOCOL_VERSION = 1;

	/**
	 * Label for deriving Alice's header key from the master key.
	 */
	String ALICE_KEY_LABEL =
			"org.briarproject.bramble.contact/ALICE_HEADER_KEY";

	/**
	 * Label for deriving Bob's header key from the master key.
	 */
	String BOB_KEY_LABEL = "org.briarproject.bramble.contact/BOB_HEADER_KEY";

	/**
	 * Label for deriving Alice's key binding nonce from the master key.
	 */
	String ALICE_NONCE_LABEL = "org.briarproject.bramble.contact/ALICE_NONCE";

	/**
	 * Label for deriving Bob's key binding nonce from the master key.
	 */
	String BOB_NONCE_LABEL = "org.briarproject.bramble.contact/BOB_NONCE";

	/**
	 * Label for signing key binding nonces.
	 */
	String SIGNING_LABEL = "org.briarproject.briar.contact/EXCHANGE";
}
