package org.briarproject.api.conversation;

import org.briarproject.api.forum.ForumInvitationMessage;

public interface ConversationForumInvitationItem extends ConversationItem {

	ForumInvitationMessage getForumInvitationMessage();
}
