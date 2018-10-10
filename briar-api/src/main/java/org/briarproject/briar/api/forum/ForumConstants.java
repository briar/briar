package org.briarproject.briar.api.forum;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public interface ForumConstants {

	/**
	 * The maximum length of a forum's name in UTF-8 bytes.
	 */
	int MAX_FORUM_NAME_LENGTH = 100;

	/**
	 * The length of a forum's random salt in bytes.
	 */
	int FORUM_SALT_LENGTH = 32;

	/**
	 * The maximum length of a forum post's text in UTF-8 bytes.
	 */
	int MAX_FORUM_POST_TEXT_LENGTH = MAX_MESSAGE_BODY_LENGTH - 1024;

	// Metadata keys
	String KEY_TIMESTAMP = "timestamp";
	String KEY_PARENT = "parent";
	String KEY_AUTHOR = "author";
	String KEY_LOCAL = "local";
	String KEY_READ = "read";

}
