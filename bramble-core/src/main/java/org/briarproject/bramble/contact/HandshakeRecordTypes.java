package org.briarproject.bramble.contact;

/**
 * Record types for the handshake protocol.
 */
interface HandshakeRecordTypes {

	byte RECORD_TYPE_EPHEMERAL_PUBLIC_KEY = 0;

	byte RECORD_TYPE_PROOF_OF_OWNERSHIP = 1;

	byte RECORD_TYPE_MINOR_VERSION = 2;
}
