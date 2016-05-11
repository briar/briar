package org.briarproject.android.contact;

import org.briarproject.api.forum.ForumInvitationMessage;

// This class is not thread-safe
public class ConversationForumInvitationInItem
		extends ConversationForumInvitationItem
		implements ConversationItem.IncomingItem {

	private boolean read;

	public ConversationForumInvitationInItem(ForumInvitationMessage fim) {
		super(fim);

		this.read = fim.isRead();
	}

	@Override
	int getType() {
		return FORUM_INVITATION_IN;
	}

	@Override
	public boolean isRead() {
		return read;
	}

	@Override
	public void setRead(boolean read) {
		this.read = read;
	}

}
