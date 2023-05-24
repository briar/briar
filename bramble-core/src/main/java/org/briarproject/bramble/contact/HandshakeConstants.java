package org.briarproject.bramble.contact;

import static org.briarproject.bramble.api.crypto.CryptoConstants.MAC_BYTES;

interface HandshakeConstants {

	/**
	 * The current major version of the handshake protocol.
	 */
	byte PROTOCOL_MAJOR_VERSION = 0;

	/**
	 * The current minor version of the handshake protocol.
	 */
	byte PROTOCOL_MINOR_VERSION = 1;

	/**
	 * Label for deriving the master key when using the deprecated v0.0 key
	 * derivation method.
	 * <p>
	 * TODO: Remove this after a reasonable migration period (added 2023-03-10).
	 */
	@Deprecated
	String MASTER_KEY_LABEL_0_0 =
			"org.briarproject.bramble.handshake/MASTER_KEY";

	/**
	 * Label for deriving the master key when using the v0.1 key derivation
	 * method.
	 */
	String MASTER_KEY_LABEL_0_1 =
			"org.briarproject.bramble.handshake/MASTER_KEY_0_1";

	/**
	 * Label for deriving Alice's proof of ownership from the master key.
	 */
	String ALICE_PROOF_LABEL = "org.briarproject.bramble.handshake/ALICE_PROOF";

	/**
	 * Label for deriving Bob's proof of ownership from the master key.
	 */
	String BOB_PROOF_LABEL = "org.briarproject.bramble.handshake/BOB_PROOF";

	/**
	 * The length of the proof of ownership in bytes.
	 */
	int PROOF_BYTES = MAC_BYTES;
}
