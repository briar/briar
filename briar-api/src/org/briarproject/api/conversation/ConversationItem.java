package org.briarproject.api.conversation;

import org.briarproject.api.sync.MessageId;

public interface ConversationItem {

	MessageId getId();

	long getTime();

	interface ContentListener {

		void contentReady();
	}

	interface Partial extends ConversationItem {

		void setContent(byte[] content);

		void setContentListener(ContentListener listener);
	}

	interface IncomingItem extends ConversationItem {

		boolean isRead();

		void setRead(boolean read);
	}

	interface OutgoingItem extends ConversationItem {

		boolean isSent();

		void setSent(boolean sent);

		boolean isSeen();

		void setSeen(boolean seen);
	}
}
