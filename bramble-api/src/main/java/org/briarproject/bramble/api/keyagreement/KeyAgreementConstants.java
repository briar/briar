package org.briarproject.bramble.api.keyagreement;

import org.briarproject.bramble.api.mailbox.MailboxConstants;

public interface KeyAgreementConstants {

	/**
	 * The current version of the BQP protocol.
	 */
	byte PROTOCOL_VERSION = 4;

	/**
	 * The QR code format identifier, used to distinguish BQP QR codes from
	 * QR codes used for other purposes. See
	 * {@link MailboxConstants#QR_FORMAT_ID}.
	 */
	byte QR_FORMAT_ID = 0;

	/**
	 * The QR code format version.
	 */
	byte QR_FORMAT_VERSION = PROTOCOL_VERSION;

	/**
	 * The length of the BQP key commitment in bytes.
	 */
	int COMMIT_LENGTH = 16;

	/**
	 * The connection timeout in milliseconds.
	 */
	long CONNECTION_TIMEOUT = 60_000;

	/**
	 * The transport identifier for Bluetooth.
	 */
	int TRANSPORT_ID_BLUETOOTH = 0;

	/**
	 * The transport identifier for LAN.
	 */
	int TRANSPORT_ID_LAN = 1;

	/**
	 * Label for deriving the shared secret.
	 */
	String SHARED_SECRET_LABEL =
			"org.briarproject.bramble.keyagreement/SHARED_SECRET";

	/**
	 * Label for deriving the master key.
	 */
	String MASTER_KEY_LABEL =
			"org.briarproject.bramble.keyagreement/MASTER_SECRET";
}
