package org.briarproject.api.privategroup;

import org.briarproject.api.identity.Author;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class JoinMessageHeader extends GroupMessageHeader {

	public JoinMessageHeader(GroupId groupId, MessageId id,
			@Nullable MessageId parentId, long timestamp, Author author,
			Author.Status authorStatus, boolean read) {
		super(groupId, id, parentId, timestamp, author, authorStatus, read);
	}

}
