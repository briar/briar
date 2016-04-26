package org.briarproject.android.contact;

import org.briarproject.api.forum.ForumInvitationMessage;

abstract class ConversationForumInvitationItem extends ConversationItem {

	private ForumInvitationMessage fim;

	public ConversationForumInvitationItem(ForumInvitationMessage fim) {
		super(fim.getId(), fim.getTimestamp());

		this.fim = fim;
	}

	public ForumInvitationMessage getForumInvitationMessage() {
		return fim;
	}

}
