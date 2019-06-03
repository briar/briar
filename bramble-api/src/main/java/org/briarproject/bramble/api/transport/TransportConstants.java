package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.crypto.SecretKey;

public interface TransportConstants {

	/**
	 * The current version of the transport protocol.
	 */
	int PROTOCOL_VERSION = 4;

	/**
	 * The length of the pseudo-random tag in bytes.
	 */
	int TAG_LENGTH = 16;

	/**
	 * The length of the stream header nonce in bytes.
	 */
	int STREAM_HEADER_NONCE_LENGTH = 24;

	/**
	 * The length of the message authentication code (MAC) in bytes.
	 */
	int MAC_LENGTH = 16;

	/**
	 * The length of the stream header plaintext in bytes. The stream header
	 * contains the protocol version, stream number and frame key.
	 */
	int STREAM_HEADER_PLAINTEXT_LENGTH = 2 + 8 + SecretKey.LENGTH;

	/**
	 * The length of the stream header in bytes.
	 */
	int STREAM_HEADER_LENGTH = STREAM_HEADER_NONCE_LENGTH
			+ STREAM_HEADER_PLAINTEXT_LENGTH + MAC_LENGTH;

	/**
	 * The length of the frame nonce in bytes.
	 */
	int FRAME_NONCE_LENGTH = 24;

	/**
	 * The length of the plaintext frame header in bytes.
	 */
	int FRAME_HEADER_PLAINTEXT_LENGTH = 4;

	/**
	 * The length of the encrypted and authenticated frame header in bytes.
	 */
	int FRAME_HEADER_LENGTH = FRAME_HEADER_PLAINTEXT_LENGTH + MAC_LENGTH;

	/**
	 * The maximum length of an encrypted and authenticated frame in bytes,
	 * including the header.
	 */
	int MAX_FRAME_LENGTH = 1024;

	/**
	 * The maximum total length of the frame payload and padding in bytes.
	 */
	int MAX_PAYLOAD_LENGTH = MAX_FRAME_LENGTH - FRAME_HEADER_LENGTH
			- MAC_LENGTH;

	/**
	 * The maximum difference in milliseconds between two peers' clocks.
	 */
	int MAX_CLOCK_DIFFERENCE = 24 * 60 * 60 * 1000; // 24 hours

	/**
	 * The size of the reordering window.
	 */
	int REORDERING_WINDOW_SIZE = 32;

	/**
	 * Label for deriving the static master key from handshake key pairs.
	 */
	String STATIC_MASTER_KEY_LABEL =
			"org.briarproject.bramble.transport/STATIC_MASTER_KEY";

	/**
	 * Label for deriving the handshake mode root key for a pending contact
	 * from the static master key.
	 */
	String PENDING_CONTACT_ROOT_KEY_LABEL =
			"org.briarproject.bramble.transport/PENDING_CONTACT_ROOT_KEY";

	/**
	 * Label for deriving the handshake mode root key for a contact from the
	 * static master key.
	 */
	String CONTACT_ROOT_KEY_LABEL =
			"org.briarproject.bramble.transport/CONTACT_ROOT_KEY";

	/**
	 * Label for deriving Alice's initial tag key from the root key in
	 * rotation mode.
	 */
	String ALICE_TAG_LABEL = "org.briarproject.bramble.transport/ALICE_TAG_KEY";

	/**
	 * Label for deriving Bob's initial tag key from the root key in rotation
	 * mode.
	 */
	String BOB_TAG_LABEL = "org.briarproject.bramble.transport/BOB_TAG_KEY";

	/**
	 * Label for deriving Alice's initial header key from the root key in
	 * rotation mode.
	 */
	String ALICE_HEADER_LABEL =
			"org.briarproject.bramble.transport/ALICE_HEADER_KEY";

	/**
	 * Label for deriving Bob's initial header key from the root key in
	 * rotation mode.
	 */
	String BOB_HEADER_LABEL =
			"org.briarproject.bramble.transport/BOB_HEADER_KEY";

	/**
	 * Label for deriving the next period's key in rotation mode.
	 */
	String ROTATE_LABEL = "org.briarproject.bramble.transport/ROTATE";

	/**
	 * Label for deriving Alice's tag key from the root key in handshake mode.
	 */
	String ALICE_HANDSHAKE_TAG_LABEL =
			"org.briarproject.bramble.transport/ALICE_HANDSHAKE_TAG_KEY";

	/**
	 * Label for deriving Bob's tag key from the root key in handshake mode.
	 */
	String BOB_HANDSHAKE_TAG_LABEL =
			"org.briarproject.bramble.transport/BOB_HANDSHAKE_TAG_KEY";

	/**
	 * Label for deriving Alice's header key from the root key in handshake
	 * mode.
	 */
	String ALICE_HANDSHAKE_HEADER_LABEL =
			"org.briarproject.bramble.transport/ALICE_HANDSHAKE_HEADER_KEY";

	/**
	 * Label for deriving Bob's header key from the root key in handshake mode.
	 */
	String BOB_HANDSHAKE_HEADER_LABEL =
			"org.briarproject.bramble.transport/BOB_HANDSHAKE_HEADER_KEY";
}
