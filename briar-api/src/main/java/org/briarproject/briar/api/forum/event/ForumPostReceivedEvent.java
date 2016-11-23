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

	private final ForumPostHeader forumPostHeader;
	private final GroupId groupId;

	public ForumPostReceivedEvent(ForumPostHeader forumPostHeader,
			GroupId groupId) {

		this.forumPostHeader = forumPostHeader;
		this.groupId = groupId;
	}

	public ForumPostHeader getForumPostHeader() {
		return forumPostHeader;
	}

	public GroupId getGroupId() {
		return groupId;
	}
}
