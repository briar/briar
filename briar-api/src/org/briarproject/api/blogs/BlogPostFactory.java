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

public interface BlogPostFactory {

	BlogPost createBlogPost(@NotNull GroupId groupId, long timestamp,
			@Nullable MessageId parent, @NotNull LocalAuthor author,
			@NotNull String body)
			throws FormatException, GeneralSecurityException;

	Message createBlogComment(GroupId groupId, LocalAuthor author,
			@Nullable String comment, MessageId originalId, MessageId wrappedId)
			throws FormatException, GeneralSecurityException;

	/** Wraps a blog post */
	Message createWrappedPost(GroupId groupId, byte[] descriptor,
			long timestamp, BdfList body)
			throws FormatException;

	/** Re-wraps a previously wrapped post */
	Message createWrappedPost(GroupId groupId, BdfList body)
			throws FormatException;

	/** Wraps a blog comment */
	Message createWrappedComment(GroupId groupId, byte[] descriptor,
			long timestamp, BdfList body, MessageId currentId)
			throws FormatException;

	/** Re-wraps a previously wrapped comment */
	Message createWrappedComment(GroupId groupId, BdfList body,
			MessageId currentId) throws FormatException;
}
