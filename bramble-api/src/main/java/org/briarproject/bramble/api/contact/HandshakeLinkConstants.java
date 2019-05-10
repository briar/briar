package org.briarproject.bramble.api.contact;

import java.util.regex.Pattern;

public interface HandshakeLinkConstants {

	/**
	 * The current version of the handshake link format.
	 */
	int FORMAT_VERSION = 0;

	/**
	 * The length of a base32-encoded handshake link in bytes, excluding the
	 * 'briar://' prefix.
	 */
	int BASE32_LINK_BYTES = 53;

	/**
	 * The length of a raw handshake link in bytes, before base32 encoding.
	 */
	int RAW_LINK_BYTES = 33;

	/**
	 * Regular expression for matching handshake links, including or excluding
	 * the 'briar://' prefix.
	 */
	Pattern LINK_REGEX =
			Pattern.compile("(briar://)?([a-z2-7]{" + BASE32_LINK_BYTES + "})");

	/**
	 * Label for hashing handshake public keys to calculate their identifiers.
	 */
	String ID_LABEL = "org.briarproject.bramble/HANDSHAKE_KEY_ID";
}
