package org.briarproject.api.privategroup;

import org.briarproject.api.clients.PostHeader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GroupMessageHeader extends PostHeader {

	private final GroupId groupId;

	public GroupMessageHeader(@NotNull GroupId groupId, @NotNull MessageId id,
			@Nullable MessageId parentId, long timestamp,
			@NotNull Author author, @NotNull Status authorStatus,
			boolean read) {
		super(id, parentId, timestamp, author, authorStatus, read);
		this.groupId = groupId;
	}

	@NotNull
	public GroupId getGroupId() {
		return groupId;
	}

}
