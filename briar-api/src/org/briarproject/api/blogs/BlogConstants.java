package org.briarproject.api.blogs;

import static org.briarproject.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public interface BlogConstants {

	/** The maximum length of a blogs's name in UTF-8 bytes. */
	int MAX_BLOG_TITLE_LENGTH = 100;

	/** The length of a blogs's description in UTF-8 bytes. */
	int MAX_BLOG_DESC_LENGTH = 240;

	/** The maximum length of a blog post's content type in UTF-8 bytes. */
	int MAX_CONTENT_TYPE_LENGTH = 50;

	/** The length of a blog post's title in UTF-8 bytes. */
	int MAX_BLOG_POST_TITLE_LENGTH = 100;

	/** The maximum length of a blog post's body in bytes. */
	int MAX_BLOG_POST_BODY_LENGTH = MAX_MESSAGE_BODY_LENGTH - 1024;

	/* Blog Sharing Constants */
	String BLOG_TITLE = "blogTitle";
	String BLOG_DESC = "blogDescription";
	String BLOG_AUTHOR_NAME = "blogAuthorName";
	String BLOG_PUBLIC_KEY = "blogPublicKey";

	// Metadata keys
	String KEY_DESCRIPTION = "description";
	String KEY_TITLE = "title";
	String KEY_TIMESTAMP = "timestamp";
	String KEY_PARENT = "parent";
	String KEY_AUTHOR_ID = "id";
	String KEY_AUTHOR_NAME = "name";
	String KEY_PUBLIC_KEY = "publicKey";
	String KEY_AUTHOR = "author";
	String KEY_CONTENT_TYPE = "contentType";
	String KEY_READ = "read";

}
