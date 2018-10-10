package org.briarproject.briar.api.blog;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public interface BlogConstants {

	/**
	 * The maximum length of a blog post's text in UTF-8 bytes.
	 */
	int MAX_BLOG_POST_TEXT_LENGTH = MAX_MESSAGE_BODY_LENGTH - 1024;

	/**
	 * The maximum length of a blog comment's text in UTF-8 bytes.
	 */
	int MAX_BLOG_COMMENT_TEXT_LENGTH = MAX_BLOG_POST_TEXT_LENGTH;

	// Metadata keys
	String KEY_TYPE = "type";
	String KEY_TIMESTAMP = "timestamp";
	String KEY_TIME_RECEIVED = "timeReceived";
	String KEY_AUTHOR = "author";
	String KEY_RSS_FEED = "rssFeed";
	String KEY_READ = "read";
	String KEY_COMMENT = "comment";
	String KEY_ORIGINAL_MSG_ID = "originalMessageId";
	String KEY_ORIGINAL_PARENT_MSG_ID = "originalParentMessageId";
	/**
	 * This is the ID of either a message wrapped from a different group
	 * or of a message from the same group that therefore needed no wrapping.
	 */
	String KEY_PARENT_MSG_ID = "parentMessageId";

}
