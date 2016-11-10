package org.briarproject.android.contact;

import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
public class ContactListItem extends ContactItem {

	private boolean connected, empty;
	private long timestamp;
	private int unread;

	public ContactListItem(Contact contact, boolean connected,
			GroupCount count) {
		super(contact);
		this.connected = connected;
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
