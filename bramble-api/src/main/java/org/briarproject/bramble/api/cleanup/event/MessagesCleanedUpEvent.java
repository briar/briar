package org.briarproject.bramble.api.cleanup.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when one or more messages in a group are
 * cleaned up.
 */
@Immutable
@NotNullByDefault
public class MessagesCleanedUpEvent extends Event {

	private final GroupId groupId;
	private final Collection<MessageId> messageIds;

	public MessagesCleanedUpEvent(GroupId groupId,
			Collection<MessageId> messageIds) {
		this.groupId = groupId;
		this.messageIds = messageIds;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public Collection<MessageId> getMessageIds() {
		return messageIds;
	}
}
