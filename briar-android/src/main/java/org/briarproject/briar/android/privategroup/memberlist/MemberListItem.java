package org.briarproject.briar.android.privategroup.memberlist;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.Author.Status;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.privategroup.GroupMember;
import org.briarproject.briar.api.privategroup.Visibility;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class MemberListItem {

	private final Author member;
	private final Status status;
	private final Visibility visibility;

	MemberListItem(GroupMember groupMember) {
		this.member = groupMember.getAuthor();
		this.visibility = groupMember.getVisibility();
		this.status = groupMember.getStatus();
	}

	Author getMember() {
		return member;
	}

	Visibility getVisibility() {
		return visibility;
	}

	Status getStatus() {
		return status;
	}

}
