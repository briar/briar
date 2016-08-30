package org.briarproject.api.blogs;

import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.briarproject.api.blogs.MessageType.COMMENT;
import static org.briarproject.api.blogs.MessageType.WRAPPED_COMMENT;

public class BlogCommentHeader extends BlogPostHeader {

	private final String comment;
	private final BlogPostHeader parent;

	public BlogCommentHeader(@NotNull MessageType type,
			@NotNull GroupId groupId, @Nullable String comment,
			@NotNull BlogPostHeader parent, @NotNull MessageId id,
			long timestamp, long timeReceived, @NotNull Author author,
			@NotNull Status authorStatus, boolean read) {

		super(type, groupId, id, parent.getId(), timestamp,
				timeReceived, author, authorStatus, read);

		if (type != COMMENT && type != WRAPPED_COMMENT)
			throw new IllegalArgumentException("Incompatible Message Type");

		this.comment = comment;
		this.parent = parent;
	}

	@Nullable
	public String getComment() {
		return comment;
	}

	public BlogPostHeader getParent() {
		return parent;
	}
}
