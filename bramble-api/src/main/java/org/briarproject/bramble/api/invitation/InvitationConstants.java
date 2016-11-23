package org.briarproject.bramble.api.invitation;

public interface InvitationConstants {

	/**
	 * The connection timeout in milliseconds.
	 */
	long CONNECTION_TIMEOUT = 60 * 1000;

	/**
	 * The confirmation timeout in milliseconds.
	 */
	long CONFIRMATION_TIMEOUT = 60 * 1000;

	/**
	 * The number of bits in an invitation or confirmation code. Codes must fit
	 * into six decimal digits.
	 */
	int CODE_BITS = 19;
}
