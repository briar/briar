package org.briarproject.conversation;

import org.briarproject.api.conversation.ConversationForumInvitationItem;
import org.briarproject.api.conversation.ConversationItem;
import org.briarproject.api.forum.ForumInvitationMessage;

class ConversationForumInvitationItemImpl {

	static ConversationItem from(ForumInvitationMessage i) {
		return i.isLocal() ? new Outgoing(i) : new Incoming(i);
	}

	static class Outgoing extends OutgoingConversationItem
			implements ConversationForumInvitationItem {

		private final ForumInvitationMessage fim;

		public Outgoing(ForumInvitationMessage fim) {
			super(fim.getId(), fim.getTimestamp(), fim.isSent(), fim.isSeen());

			this.fim = fim;
		}

		@Override
		public ForumInvitationMessage getForumInvitationMessage() {
			return fim;
		}
	}

	static class Incoming extends IncomingConversationItem
			implements ConversationForumInvitationItem {

		private final ForumInvitationMessage fim;

		public Incoming(ForumInvitationMessage fim) {
			super(fim.getId(), fim.getTimestamp(), fim.isRead());

			this.fim = fim;
		}

		@Override
		public ForumInvitationMessage getForumInvitationMessage() {
			return fim;
		}
	}
}
