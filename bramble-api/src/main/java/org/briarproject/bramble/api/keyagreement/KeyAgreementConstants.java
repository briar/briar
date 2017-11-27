package org.briarproject.bramble.api.keyagreement;

public interface KeyAgreementConstants {

	/**
	 * The current version of the BQP protocol.
	 */
	byte PROTOCOL_VERSION = 3;

	/**
	 * The length of the record header in bytes.
	 */
	int RECORD_HEADER_LENGTH = 4;

	/**
	 * The offset of the payload length in the record header, in bytes.
	 */
	int RECORD_HEADER_PAYLOAD_LENGTH_OFFSET = 2;

	/**
	 * The length of the BQP key commitment in bytes.
	 */
	int COMMIT_LENGTH = 16;

	/**
	 * The connection timeout in milliseconds.
	 */
	long CONNECTION_TIMEOUT = 20 * 1000;

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
	 * Label for deriving the master secret.
	 */
	String MASTER_SECRET_LABEL =
			"org.briarproject.bramble.keyagreement/MASTER_SECRET";
}
