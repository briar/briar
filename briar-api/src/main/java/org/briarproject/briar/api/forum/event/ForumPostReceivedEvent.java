package org.briarproject.briar.api.forum.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.forum.ForumPostHeader;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a new forum post is received.
 */
@Immutable
@NotNullByDefault
public class ForumPostReceivedEvent extends Event {

	private final GroupId groupId;
	private final ForumPostHeader header;
	private final String text;

	public ForumPostReceivedEvent(GroupId groupId, ForumPostHeader header,
			String text) {
		this.groupId = groupId;
		this.header = header;
		this.text = text;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public ForumPostHeader getHeader() {
		return header;
	}

	public String getText() {
		return text;
	}
}
