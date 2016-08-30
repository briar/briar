package org.briarproject.api.blogs;

import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlogPost extends ForumPost {

	public BlogPost(@NotNull Message message, @Nullable MessageId parent,
			@NotNull Author author) {
		super(message, parent, author);
	}
}
