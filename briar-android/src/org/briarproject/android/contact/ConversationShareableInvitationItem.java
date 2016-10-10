package org.briarproject.android.contact;

import org.briarproject.api.sharing.InvitationRequest;

abstract class ConversationShareableInvitationItem extends ConversationItem {

	private final InvitationRequest fim;

	ConversationShareableInvitationItem(InvitationRequest fim) {
		super(fim.getId(), fim.getGroupId(), fim.getTimestamp());

		this.fim = fim;
	}

	InvitationRequest getInvitationRequest() {
		return fim;
	}
}
