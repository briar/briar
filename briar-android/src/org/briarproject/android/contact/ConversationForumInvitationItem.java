package org.briarproject.android.contact;

import org.briarproject.api.forum.ForumInvitationRequest;

abstract class ConversationForumInvitationItem extends ConversationItem {

	private final ForumInvitationRequest fim;

	public ConversationForumInvitationItem(ForumInvitationRequest fim) {
		super(fim.getId(), fim.getTimestamp());

		this.fim = fim;
	}

	public ForumInvitationRequest getForumInvitationMessage() {
		return fim;
	}
}
