package org.briarproject.api.blogs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;

public class BlogPost extends ForumPost {

	@Nullable
	private final String title;
	@NotNull
	private final String teaser;
	private final boolean hasBody;

	public BlogPost(@Nullable String title, @NotNull String teaser,
			boolean hasBody, @NotNull Message message,
			@Nullable MessageId parent, @NotNull Author author,
			@NotNull String contentType) {
		super(message, parent, author, contentType);

		this.title = title;
		this.teaser = teaser;
		this.hasBody = hasBody;
	}

	@Nullable
	public String getTitle() {
		return title;
	}

	@NotNull
	public String getTeaser() {
		return teaser;
	}

	public boolean hasBody() {
		return hasBody;
	}
}
