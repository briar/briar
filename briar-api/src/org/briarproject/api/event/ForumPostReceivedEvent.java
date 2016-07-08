package org.briarproject.api.event;

import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.sync.GroupId;

/**
 * An event that is broadcast when a new forum post was received.
 */
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
