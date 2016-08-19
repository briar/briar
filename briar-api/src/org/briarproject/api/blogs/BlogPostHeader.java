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
	private final long timeReceived;

	public BlogPostHeader(@Nullable String title, @NotNull MessageId id,
			long timestamp, long timeReceived, @NotNull Author author,
			@NotNull Status authorStatus, @NotNull String contentType,
			boolean read) {
		super(id, null, timestamp, author, authorStatus, contentType, read);

		this.title = title;
		this.timeReceived = timeReceived;
	}

	@Nullable
	public String getTitle() {
		return title;
	}

	public long getTimeReceived() {
		return timeReceived;
	}

}
