package org.briarproject.api.blogs;

import static org.briarproject.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public interface BlogConstants {

	/** The maximum length of a blogs's name in UTF-8 bytes. */
	int MAX_BLOG_TITLE_LENGTH = 100;

	/** The length of a blogs's description in UTF-8 bytes. */
	int MAX_BLOG_DESC_LENGTH = 240;

	/** The maximum length of a blog post's body in bytes. */
	int MAX_BLOG_POST_BODY_LENGTH = MAX_MESSAGE_BODY_LENGTH - 1024;

	/** The maximum length of a blog comment in bytes. */
	int MAX_BLOG_COMMENT_LENGTH = MAX_BLOG_POST_BODY_LENGTH;

	/* Blog Sharing Constants */
	String BLOG_AUTHOR_NAME = "blogAuthorName";
	String BLOG_PUBLIC_KEY = "blogPublicKey";

	// Metadata keys
	String KEY_TYPE = "type";
	String KEY_DESCRIPTION = "description";
	String KEY_TIMESTAMP = "timestamp";
	String KEY_TIME_RECEIVED = "timeReceived";
	String KEY_AUTHOR_ID = "id";
	String KEY_AUTHOR_NAME = "name";
	String KEY_PUBLIC_KEY = "publicKey";
	String KEY_AUTHOR = "author";
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
