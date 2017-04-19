package org.briarproject.briar.api.blog;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;

import java.security.GeneralSecurityException;

import javax.annotation.Nullable;

import static org.briarproject.briar.api.blog.BlogManager.CLIENT_ID;

@NotNullByDefault
public interface BlogPostFactory {

	String SIGNING_LABEL_POST = CLIENT_ID + "/POST";
	String SIGNING_LABEL_COMMENT = CLIENT_ID + "/COMMENT";

	BlogPost createBlogPost(GroupId groupId, long timestamp,
			@Nullable MessageId parent, LocalAuthor author, String body)
			throws FormatException, GeneralSecurityException;

	Message createBlogComment(GroupId groupId, LocalAuthor author,
			@Nullable String comment, MessageId parentOriginalId,
			MessageId parentCurrentId)
			throws FormatException, GeneralSecurityException;

	/**
	 * Wraps a blog post
	 */
	Message wrapPost(GroupId groupId, byte[] descriptor, long timestamp,
			BdfList body) throws FormatException;

	/**
	 * Re-wraps a previously wrapped post
	 */
	Message rewrapWrappedPost(GroupId groupId, BdfList body)
			throws FormatException;

	/**
	 * Wraps a blog comment
	 */
	Message wrapComment(GroupId groupId, byte[] descriptor, long timestamp,
			BdfList body, MessageId parentCurrentId) throws FormatException;

	/**
	 * Re-wraps a previously wrapped comment
	 */
	Message rewrapWrappedComment(GroupId groupId, BdfList body,
			MessageId parentCurrentId) throws FormatException;
}
