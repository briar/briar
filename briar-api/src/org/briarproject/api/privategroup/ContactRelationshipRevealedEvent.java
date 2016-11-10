package org.briarproject.api.privategroup;

import org.briarproject.api.event.Event;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ContactRelationshipRevealedEvent extends Event {

	private final GroupId groupId;
	private final Visibility visibility;

	public ContactRelationshipRevealedEvent(GroupId groupId,
			Visibility visibility) {
		this.groupId = groupId;
		this.visibility = visibility;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public Visibility getVisibility() {
		return visibility;
	}

}
