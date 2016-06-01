package org.briarproject.api.event;

import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.sync.GroupId;

/**
 * An event that is broadcast when a new private message was received.
 */
public class PrivateMessageReceivedEvent extends Event {

	private final PrivateMessageHeader messageHeader;
	private final GroupId groupId;

	public PrivateMessageReceivedEvent(PrivateMessageHeader messageHeader,
			GroupId groupId) {
		this.messageHeader = messageHeader;
		this.groupId = groupId;
	}

	public PrivateMessageHeader getMessageHeader() {
		return messageHeader;
	}

	public GroupId getGroupId() {
		return groupId;
	}
}
