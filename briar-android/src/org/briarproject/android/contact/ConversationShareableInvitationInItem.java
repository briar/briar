package org.briarproject.android.contact;

import org.briarproject.api.blogs.BlogInvitationRequest;
import org.briarproject.api.forum.ForumInvitationRequest;
import org.briarproject.api.sharing.InvitationRequest;

// This class is not thread-safe
class ConversationShareableInvitationInItem
		extends ConversationShareableInvitationItem
		implements ConversationItem.IncomingItem {

	private final int type;
	private boolean read;

	ConversationShareableInvitationInItem(InvitationRequest ir) {
		super(ir);

		if (ir instanceof ForumInvitationRequest) {
			this.type = FORUM_INVITATION_IN;
		} else if (ir instanceof BlogInvitationRequest) {
			this.type = BLOG_INVITATION_IN;
		} else {
			throw new IllegalArgumentException("Unknown Invitation Type.");
		}

		this.read = ir.isRead();
	}

	@Override
	int getType() {
		return type;
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
