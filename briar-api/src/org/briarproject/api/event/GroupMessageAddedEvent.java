package org.briarproject.api.event;

import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.sync.GroupId;

/**
 * An event that is broadcast when a private group message was added
 * to the database.
 */
public class GroupMessageAddedEvent extends Event {

	private final GroupId groupId;
	private final GroupMessageHeader header;
	private final boolean local;

	public GroupMessageAddedEvent(GroupId groupId, GroupMessageHeader header,
			boolean local) {

		this.groupId = groupId;
		this.header = header;
		this.local = local;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public GroupMessageHeader getHeader() {
		return header;
	}

	public boolean isLocal() {
		return local;
	}

}
