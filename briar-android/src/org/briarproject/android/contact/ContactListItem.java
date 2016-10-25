package org.briarproject.android.contact;

import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
public class ContactListItem extends ContactItem {

	private final GroupId groupId;
	private boolean empty;
	private long timestamp;
	private int unread;

	public ContactListItem(Contact contact,	boolean connected, GroupId groupId,
			GroupCount count) {
		super(contact, connected);
		this.groupId = groupId;
		this.empty = count.getMsgCount() == 0;
		this.unread = count.getUnreadCount();
		this.timestamp = count.getLatestMsgTime();
	}

	void addMessage(ConversationItem message) {
		empty = false;
		if (message.getTime() > timestamp) timestamp = message.getTime();
		if (!message.isRead())
			unread++;
	}

	GroupId getGroupId() {
		return groupId;
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
