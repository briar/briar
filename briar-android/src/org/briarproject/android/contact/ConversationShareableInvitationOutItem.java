package org.briarproject.android.contact;

import org.briarproject.api.blogs.BlogInvitationRequest;
import org.briarproject.api.forum.ForumInvitationRequest;
import org.briarproject.api.sharing.InvitationRequest;

/**
 * This class is needed and can not be replaced by an ConversationNoticeOutItem,
 * because it carries the optional invitation message
 * to be displayed as a regular private message.
 * <p/>
 * This class is not thread-safe
 */
class ConversationShareableInvitationOutItem
		extends ConversationShareableInvitationItem
		implements ConversationItem.OutgoingItem {

	private final int type;
	private boolean sent, seen;

	ConversationShareableInvitationOutItem(InvitationRequest ir) {
		super(ir);

		if (ir instanceof ForumInvitationRequest) {
			this.type = FORUM_INVITATION_OUT;
		} else if (ir instanceof BlogInvitationRequest) {
			this.type = BLOG_INVITATION_OUT;
		} else {
			throw new IllegalArgumentException("Unknown Invitation Type.");
		}

		this.sent = ir.isSent();
		this.seen = ir.isSeen();
	}

	@Override
	int getType() {
		return type;
	}

	@Override
	public boolean isSent() {
		return sent;
	}

	@Override
	public void setSent(boolean sent) {
		this.sent = sent;
	}

	@Override
	public boolean isSeen() {
		return seen;
	}

	@Override
	public void setSeen(boolean seen) {
		this.seen = seen;
	}
}
