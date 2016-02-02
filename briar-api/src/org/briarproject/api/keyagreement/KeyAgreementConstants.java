package org.briarproject.api.keyagreement;


public interface KeyAgreementConstants {

	/** The current version of the BQP protocol. */
	byte PROTOCOL_VERSION = 1;

	/** The length of the record header in bytes. */
	int RECORD_HEADER_LENGTH = 4;

	/** The offset of the payload length in the record header, in bytes. */
	int RECORD_HEADER_PAYLOAD_LENGTH_OFFSET = 2;

	/** The length of the BQP key commitment in bytes. */
	int COMMIT_LENGTH = 16;

	long CONNECTION_TIMEOUT = 20 * 1000; // Milliseconds
}
