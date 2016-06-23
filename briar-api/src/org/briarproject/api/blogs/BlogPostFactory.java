package org.briarproject.api.blogs;

import org.briarproject.api.FormatException;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.GeneralSecurityException;

public interface BlogPostFactory {

	BlogPost createBlogPost(@NotNull GroupId groupId, @Nullable String title,
			long timestamp, @Nullable MessageId parent,
			@NotNull LocalAuthor author, @NotNull String contentType,
			@NotNull byte[] body)
			throws FormatException, GeneralSecurityException;
}
