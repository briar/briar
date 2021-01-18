package org.briarproject.briar.android.contact;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ContactListItem extends ContactItem
		implements Comparable<ContactListItem> {

	private final boolean empty;
	private final long timestamp;
	private final int unread;

	public ContactListItem(Contact contact, boolean connected,
			GroupCount count) {
		super(contact, connected);
		this.empty = count.getMsgCount() == 0;
		this.unread = count.getUnreadCount();
		this.timestamp = count.getLatestMsgTime();
	}

	private ContactListItem(Contact contact, boolean connected, boolean empty,
			int unread, long timestamp) {
		super(contact, connected);
		this.empty = empty;
		this.timestamp = timestamp;
		this.unread = unread;
	}

	ContactListItem(ContactListItem item, boolean connected) {
		this(item.getContact(), connected, item.empty, item.unread,
				item.timestamp);
	}

	ContactListItem(ContactListItem item, ConversationMessageHeader h) {
		this(item.getContact(), item.isConnected(), false,
				h.isRead() ? item.unread : item.unread + 1,
				Math.max(h.getTimestamp(), item.timestamp));
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

	@Override
	public int compareTo(ContactListItem o) {
		return Long.compare(o.getTimestamp(), timestamp);
	}
}
