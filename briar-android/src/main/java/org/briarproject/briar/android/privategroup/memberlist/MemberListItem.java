package org.briarproject.briar.android.privategroup.memberlist;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorInfo;
import org.briarproject.bramble.api.identity.AuthorInfo.Status;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionStatus;
import org.briarproject.briar.api.privategroup.GroupMember;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class MemberListItem {

	private final GroupMember groupMember;
	private ConnectionStatus status;

	MemberListItem(GroupMember groupMember, ConnectionStatus status) {
		this.groupMember = groupMember;
		this.status = status;
	}

	Author getMember() {
		return groupMember.getAuthor();
	}

	AuthorInfo getAuthorInfo() {
		return groupMember.getAuthorInfo();
	}

	Status getStatus() {
		return groupMember.getAuthorInfo().getStatus();
	}

	boolean isCreator() {
		return groupMember.isCreator();
	}

	@Nullable
	ContactId getContactId() {
		return groupMember.getContactId();
	}

	ConnectionStatus getConnectionStatus() {
		return status;
	}

	void setConnectionStatus(ConnectionStatus status) {
		this.status = status;
	}

}
