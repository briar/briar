package org.briarproject.briar.api.privategroup.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a private group is dissolved by a remote
 * creator.
 */
@Immutable
@NotNullByDefault
public class GroupDissolvedEvent extends Event {

	private final GroupId groupId;

	public GroupDissolvedEvent(GroupId groupId) {
		this.groupId = groupId;
	}

	public GroupId getGroupId() {
		return groupId;
	}

}
