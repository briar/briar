package org.briarproject.api.blogs;

import org.briarproject.api.clients.PostHeader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlogPostHeader extends PostHeader {

	private final MessageType type;
	private final GroupId groupId;
	private final long timeReceived;

	public BlogPostHeader(@NotNull MessageType type, @NotNull GroupId groupId,
			@NotNull MessageId id, @Nullable MessageId parentId, long timestamp,
			long timeReceived, @NotNull Author author,
			@NotNull Status authorStatus, boolean read) {
		super(id, parentId, timestamp, author, authorStatus, read);

		this.type = type;
		this.groupId = groupId;
		this.timeReceived = timeReceived;
	}

	public BlogPostHeader(@NotNull MessageType type, @NotNull GroupId groupId,
			@NotNull MessageId id, long timestamp, long timeReceived,
			@NotNull Author author, @NotNull Status authorStatus,
			boolean read) {
		this(type, groupId, id, null, timestamp, timeReceived, author,
				authorStatus, read);
	}

	public MessageType getType() {
		return type;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public long getTimeReceived() {
		return timeReceived;
	}
}
