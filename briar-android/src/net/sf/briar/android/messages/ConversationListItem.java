package net.sf.briar.android.messages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.sf.briar.android.DescendingHeaderComparator;
import net.sf.briar.api.AuthorId;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.PrivateMessageHeader;

class ConversationListItem {

	private final Contact contact;
	private final boolean empty;
	private final long timestamp;
	private final int unread;

	ConversationListItem(Contact contact,
			Collection<PrivateMessageHeader> headers) {
		this.contact = contact;
		empty = headers.isEmpty();
		if(empty) {
			timestamp = 0;
			unread = 0;
		} else {
			List<PrivateMessageHeader> list =
					new ArrayList<PrivateMessageHeader>(headers);
			Collections.sort(list, DescendingHeaderComparator.INSTANCE);
			timestamp = list.get(0).getTimestamp();
			int unread = 0;
			for(PrivateMessageHeader h : list) if(!h.isRead()) unread++;
			this.unread = unread;
		}
	}

	ContactId getContactId() {
		return contact.getId();
	}

	String getContactName() {
		return contact.getAuthor().getName();
	}

	AuthorId getLocalAuthorId() {
		return contact.getLocalAuthorId();
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
