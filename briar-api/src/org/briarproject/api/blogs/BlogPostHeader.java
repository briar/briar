package org.briarproject.api.blogs;

import org.briarproject.api.clients.PostHeader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlogPostHeader extends PostHeader {

	@Nullable
	private final String title;
	@NotNull
	private final String teaser;
	private final boolean hasBody;

	public BlogPostHeader(@Nullable String title, @NotNull String teaser,
			boolean hasBody, @NotNull MessageId id,
			@Nullable MessageId parentId, long timestamp,
			@NotNull Author author,	@NotNull Status authorStatus,
			@NotNull String contentType, boolean read) {
		super(id, parentId, timestamp, author, authorStatus, contentType, read);

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
