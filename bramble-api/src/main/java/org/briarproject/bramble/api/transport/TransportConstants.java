package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.crypto.SecretKey;

public interface TransportConstants {

	/**
	 * The length of the pseudo-random tag in bytes.
	 */
	int TAG_LENGTH = 16;

	/**
	 * The length of the stream header nonce in bytes.
	 */
	int STREAM_HEADER_NONCE_LENGTH = 24;

	/**
	 * The length of the stream header initialisation vector (IV) in bytes.
	 */
	int STREAM_HEADER_IV_LENGTH = STREAM_HEADER_NONCE_LENGTH - 8;

	/**
	 * The length of the message authentication code (MAC) in bytes.
	 */
	int MAC_LENGTH = 16;

	/**
	 * The length of the stream header in bytes.
	 */
	int STREAM_HEADER_LENGTH = STREAM_HEADER_IV_LENGTH + SecretKey.LENGTH
			+ MAC_LENGTH;

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
	 * The minimum stream length in bytes that all transport plugins must
	 * support. Streams may be shorter than this length, but all transport
	 * plugins must support streams of at least this length.
	 */
	int MIN_STREAM_LENGTH = STREAM_HEADER_LENGTH + FRAME_HEADER_LENGTH
			+ MAC_LENGTH;

	/**
	 * The maximum difference in milliseconds between two peers' clocks.
	 */
	int MAX_CLOCK_DIFFERENCE = 24 * 60 * 60 * 1000; // 24 hours

	/**
	 * The size of the reordering window.
	 */
	int REORDERING_WINDOW_SIZE = 32;
}
