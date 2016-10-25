package org.briarproject.android.privategroup.memberlist;

import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.privategroup.GroupMember;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
public class MemberListItem {

	private final Author member;
	private Status status;
	private boolean sharing;

	public MemberListItem(GroupMember groupMember) {
		this.member = groupMember.getAuthor();
		this.sharing = groupMember.isShared();
		this.status = groupMember.getStatus();
	}

	public Author getMember() {
		return member;
	}

	public boolean isSharing() {
		return sharing;
	}

	public Status getStatus() {
		return status;
	}

}
