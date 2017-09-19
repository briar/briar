package org.briarproject.briar.api.privategroup.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.privategroup.GroupMessageHeader;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a private group message was added
 * to the database.
 */
@Immutable
@NotNullByDefault
public class GroupMessageAddedEvent extends Event {

	private final GroupId groupId;
	private final GroupMessageHeader header;
	private final String body;
	private final boolean local;

	public GroupMessageAddedEvent(GroupId groupId, GroupMessageHeader header,
			String body, boolean local) {
		this.groupId = groupId;
		this.header = header;
		this.body = body;
		this.local = local;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public GroupMessageHeader getHeader() {
		return header;
	}

	public String getBody() {
		return body;
	}

	public boolean isLocal() {
		return local;
	}

}
