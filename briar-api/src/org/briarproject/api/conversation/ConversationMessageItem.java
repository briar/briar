package org.briarproject.api.conversation;

import org.briarproject.api.conversation.ConversationItem.Partial;
import org.briarproject.api.messaging.PrivateMessageHeader;

public interface ConversationMessageItem extends Partial {

	PrivateMessageHeader getHeader();

	byte[] getBody();
}
