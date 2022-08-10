package org.briarproject.bramble.api.sync.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import java.util.Map;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a message is shared.
 */
@Immutable
@NotNullByDefault
public class MessageSharedEvent extends Event {

	private final MessageId messageId;
	private final GroupId groupId;
	private final Map<ContactId, Boolean> groupVisibility;

	public MessageSharedEvent(MessageId message, GroupId groupId,
			Map<ContactId, Boolean> groupVisibility) {
		this.messageId = message;
		this.groupId = groupId;
		this.groupVisibility = groupVisibility;
	}

	public MessageId getMessageId() {
		return messageId;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public Map<ContactId, Boolean> getGroupVisibility() {
		return groupVisibility;
	}
}
