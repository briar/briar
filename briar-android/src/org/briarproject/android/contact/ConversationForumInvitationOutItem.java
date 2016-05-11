package org.briarproject.android.contact;

import org.briarproject.api.forum.ForumInvitationMessage;

/**
 * This class is needed and can not be replaced by an ConversationNoticeOutItem,
 * because it carries the optional invitation message
 * to be displayed as a regular private message.
 * <p/>
 * This class is not thread-safe
 */
public class ConversationForumInvitationOutItem
		extends ConversationForumInvitationItem
		implements ConversationItem.OutgoingItem {

	private boolean sent, seen;

	public ConversationForumInvitationOutItem(ForumInvitationMessage fim) {
		super(fim);
		this.sent = fim.isSent();
		this.seen = fim.isSeen();
	}

	@Override
	int getType() {
		return FORUM_INVITATION_OUT;
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
