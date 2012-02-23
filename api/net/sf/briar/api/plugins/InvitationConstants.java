package net.sf.briar.api.plugins;

public interface InvitationConstants {

	static final long INVITATION_TIMEOUT = 60 * 1000; // 1 minute

	static final int CODE_BITS = 19; // Codes must fit into six decimal digits

	static final int MAX_CODE = 1 << CODE_BITS - 1;

	static final int HASH_LENGTH = 48;

	static final int MAX_PUBLIC_KEY_LENGTH = 120;
}
