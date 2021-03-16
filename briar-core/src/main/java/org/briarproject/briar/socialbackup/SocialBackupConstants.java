package org.briarproject.briar.socialbackup;

public interface SocialBackupConstants {

	// Group metadata keys
	String GROUP_KEY_CONTACT_ID = "contactId";
	String GROUP_KEY_SECRET = "secret";
	String GROUP_KEY_CUSTODIANS = "custodians";
	String GROUP_KEY_THRESHOLD = "threshold";
	String GROUP_KEY_VERSION = "version";

	// Message metadata keys
	String MSG_KEY_TIMESTAMP = "timestamp";
	String MSG_KEY_MESSAGE_TYPE = "messageType";
	String MSG_KEY_LOCAL = "local";
	String MSG_KEY_VERSION = "version";

	/**
	 * The length of the authenticated cipher's nonce in bytes.
	 */
	int NONCE_BYTES = 24;

	/**
	 * The length of the authenticated cipher's authentication tag in bytes.
	 */
	int AUTH_TAG_BYTES = 16;

	/**
	 * The length of the secret ID in bytes.
	 */
	int SECRET_ID_BYTES = 32;

	/**
	 * The maximum length of a shard in bytes.
	 */
	int MAX_SHARD_BYTES = 1024;
}
