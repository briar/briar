package net.sf.briar.android.messages;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sf.briar.android.DescendingHeaderComparator;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.PrivateMessageHeader;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageId;

// This class is not thread-safe
class ConversationListItem {

	private final Set<MessageId> messageIds = new HashSet<MessageId>();
	private final Contact contact;
	private String subject;
	private long timestamp;
	private int unread;

	ConversationListItem(Contact contact, List<PrivateMessageHeader> headers) {
		if(headers.isEmpty()) throw new IllegalArgumentException();
		this.contact = contact;
		Collections.sort(headers, DescendingHeaderComparator.INSTANCE);
		subject = headers.get(0).getSubject();
		timestamp = headers.get(0).getTimestamp();
		unread = 0;
		for(PrivateMessageHeader h : headers) {
			if(!h.isRead()) unread++;
			if(!messageIds.add(h.getId())) throw new IllegalArgumentException();
		}
	}

	ConversationListItem(Contact contact, Message first, boolean incoming) {
		this.contact = contact;
		subject = first.getSubject();
		timestamp = first.getTimestamp();
		unread = incoming ? 1 : 0;
		messageIds.add(first.getId());
	}

	boolean add(Message m, boolean incoming) {
		if(!messageIds.add(m.getId())) return false;
		if(m.getTimestamp() > timestamp) {
			// The added message is the newest
			subject = m.getSubject();
			timestamp = m.getTimestamp();
		}
		if(incoming) unread++;
		return true;
	}

	ContactId getContactId() {
		return contact.getId();
	}

	String getContactName() {
		return contact.getName();
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
