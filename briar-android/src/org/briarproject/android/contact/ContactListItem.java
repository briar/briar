package org.briarproject.android.contact;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.conversation.ConversationItem;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

// This class is not thread-safe
public class ContactListItem {

	private final Contact contact;
	private final LocalAuthor localAuthor;
	private final GroupId groupId;
	private boolean connected, empty;
	private long timestamp;
	private int unread;

	public ContactListItem(Contact contact, LocalAuthor localAuthor,
			boolean connected,
			GroupId groupId,
			Collection<ConversationItem> messages) {
		this.contact = contact;
		this.localAuthor = localAuthor;
		this.groupId = groupId;
		this.connected = connected;
		setMessages(messages);
	}

	void setMessages(Collection<ConversationItem> messages) {
		empty = messages == null || messages.isEmpty();
		timestamp = 0;
		unread = 0;
		if (!empty) {
			for (ConversationItem i : messages) {
				addMessage(i);
			}
		}
	}

	void addMessage(ConversationItem message) {
		empty = empty && message == null;
		if (message != null) {
			if (message.getTime() > timestamp) timestamp = message.getTime();
			if (message instanceof ConversationItem.IncomingItem &&
					!((ConversationItem.IncomingItem) message).isRead())
				unread++;
		}
	}

	public Contact getContact() {
		return contact;
	}

	public LocalAuthor getLocalAuthor() {
		return localAuthor;
	}

	GroupId getGroupId() {
		return groupId;
	}

	boolean isConnected() {
		return connected;
	}

	void setConnected(boolean connected) {
		this.connected = connected;
	}

	boolean isEmpty() {
		return empty;
	}

	long getTimestamp() {
		return timestamp;
	}

	int getUnreadCount() {
		return unread;
	}
}