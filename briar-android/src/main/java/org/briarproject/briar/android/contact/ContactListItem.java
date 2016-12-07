package org.briarproject.briar.android.contact;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
public class ContactListItem extends ContactItem {

	private boolean empty;
	private long timestamp;
	private int unread;

	public ContactListItem(Contact contact, boolean connected,
			GroupCount count) {
		super(contact, connected);
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
