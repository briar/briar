package net.sf.briar.android.messages;

import java.util.Collections;
import java.util.List;

import net.sf.briar.android.DescendingHeaderComparator;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.PrivateMessageHeader;

class ConversationListItem {

	private final Contact contact;
	private final String subject;
	private final long timestamp;
	private final int unread;

	ConversationListItem(Contact contact, List<PrivateMessageHeader> headers) {
		if(headers.isEmpty()) throw new IllegalArgumentException();
		this.contact = contact;
		Collections.sort(headers, DescendingHeaderComparator.INSTANCE);
		subject = headers.get(0).getSubject();
		timestamp = headers.get(0).getTimestamp();
		int unread = 0;
		for(PrivateMessageHeader h : headers) if(!h.isRead()) unread++;
		this.unread = unread;
	}

	ContactId getContactId() {
		return contact.getId();
	}

	String getContactName() {
		return contact.getAuthor().getName();
	}

	String getSubject() {
		return subject;
	}

	long getTimestamp() {
		return timestamp;
	}

	int getUnreadCount() {
		return unread;
	}
}
