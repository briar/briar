package net.sf.briar.android.messages;

import java.util.Collections;
import java.util.List;

import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.PrivateMessageHeader;

class ConversationListItem {

	private final ContactId contactId;
	private final String name, subject;
	private final long timestamp;
	private final int length;
	private final boolean read;

	ConversationListItem(Contact contact, List<PrivateMessageHeader> headers) {
		if(headers.isEmpty()) throw new IllegalArgumentException();
		contactId = contact.getId();
		name = contact.getName();
		Collections.sort(headers, DescendingHeaderComparator.INSTANCE);
		subject = headers.get(0).getSubject();
		timestamp = headers.get(0).getTimestamp();
		length = headers.size();
		boolean allRead = true;
		for(PrivateMessageHeader h : headers) allRead &= h.isRead();
		read = allRead;
	}

	ContactId getContactId() {
		return contactId;
	}

	String getName() {
		return name;
	}

	String getSubject() {
		return subject;
	}

	long getTimestamp() {
		return timestamp;
	}

	boolean isRead() {
		return read;
	}

	int getLength() {
		return length;
	}
}
