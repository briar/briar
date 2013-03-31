package net.sf.briar.api.invitation;

public interface InvitationConstants {

	long CONNECTION_TIMEOUT = 30 * 1000; // Milliseconds

	long CONFIRMATION_TIMEOUT = 60 * 1000; // Milliseconds

	int CODE_BITS = 19; // Codes must fit into six decimal digits

	int HASH_LENGTH = 48; // Bytes

	int MAX_PUBLIC_KEY_LENGTH = 97; // Bytes
}
