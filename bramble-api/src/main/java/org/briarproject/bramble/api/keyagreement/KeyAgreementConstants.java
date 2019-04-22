package org.briarproject.bramble.api.keyagreement;

public interface KeyAgreementConstants {

	/**
	 * The version of the BQP protocol used in beta releases. This version
	 * number is reserved.
	 */
	byte BETA_PROTOCOL_VERSION = 89;

	/**
	 * The current version of the BQP protocol.
	 */
	byte PROTOCOL_VERSION = 4;

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
