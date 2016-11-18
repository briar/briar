package org.briarproject.api.blogs;

import org.briarproject.api.FormatException;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.GeneralSecurityException;

import static org.briarproject.api.blogs.BlogManager.CLIENT_ID;

public interface BlogPostFactory {

	String SIGNING_LABEL_POST = CLIENT_ID + "/POST";
	String SIGNING_LABEL_COMMENT = CLIENT_ID + "/COMMENT";

	BlogPost createBlogPost(@NotNull GroupId groupId, long timestamp,
			@Nullable MessageId parent, @NotNull LocalAuthor author,
			@NotNull String body)
			throws FormatException, GeneralSecurityException;

	Message createBlogComment(GroupId groupId, LocalAuthor author,
			@Nullable String comment, MessageId originalId, MessageId wrappedId)
			throws FormatException, GeneralSecurityException;

	/** Wraps a blog post */
	Message wrapPost(GroupId groupId, byte[] descriptor,
			long timestamp, BdfList body)
			throws FormatException;

	/** Re-wraps a previously wrapped post */
	Message rewrapWrappedPost(GroupId groupId, BdfList body)
			throws FormatException;

	/** Wraps a blog comment */
	Message wrapComment(GroupId groupId, byte[] descriptor,
			long timestamp, BdfList body, MessageId currentId)
			throws FormatException;

	/** Re-wraps a previously wrapped comment */
	Message rewrapWrappedComment(GroupId groupId, BdfList body,
			MessageId currentId) throws FormatException;
}
