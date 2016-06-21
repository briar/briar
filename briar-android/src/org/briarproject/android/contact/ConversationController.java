package org.briarproject.android.contact;

import org.briarproject.android.controller.ActivityLifecycleController;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.conversation.ConversationItem;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;
import java.util.List;

public interface ConversationController extends ActivityLifecycleController {

	void loadConversation(GroupId groupId,
			UiResultHandler<Boolean> resultHandler);

	void loadMessages(UiResultHandler<Boolean> resultHandler);

	void createMessage(byte[] body, long timestamp,
			UiResultHandler<ConversationItem> resultHandler);

	ContactId getContactId();

	String getContactName();

	byte[] getContactIdenticonKey();

	List<ConversationItem> getConversationItems();

	boolean isConnected();

	void markMessagesRead(Collection<ConversationItem> unread,
			UiResultHandler<Boolean> resultHandler);

	void markNewMessageRead(ConversationItem item);

	void removeContact(UiResultHandler<Boolean> resultHandler);

	void respondToItem(ConversationItem item, boolean accept, long minTimestamp,
			UiResultHandler<Boolean> resultHandler);

	void shouldHideIntroductionAction(UiResultHandler<Boolean> resultHandler);

	interface ConversationListener {
		void contactUpdated();

		void markMessages(Collection<MessageId> messageIds, boolean sent,
				boolean seen);

		void messageReceived(ConversationItem item);
	}
}
