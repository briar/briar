package net.sf.briar.android.messages;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.sf.briar.api.Contact;
import net.sf.briar.api.db.PrivateMessageHeader;

class ConversationListItem {

	static final Comparator<ConversationListItem> COMPARATOR =
			new ItemComparator();

	private static final Comparator<PrivateMessageHeader> HEADER_COMPARATOR =
			new HeaderComparator();

	private final Contact contact;
	private final List<PrivateMessageHeader> headers;
	private final boolean read, starred;

	ConversationListItem(Contact contact, List<PrivateMessageHeader> headers) {
		if(headers.isEmpty()) throw new IllegalArgumentException();
		Collections.sort(headers, HEADER_COMPARATOR);
		boolean read = false, starred = false;
		for(PrivateMessageHeader h : headers) {
			read &= h.getRead();
			starred |= h.getStarred();
		}
		this.contact = contact;
		this.headers = headers;
		this.read = read;
		this.starred = starred;
	}

	String getName() {
		return contact.getName();
	}

	String getSubject() {
		return headers.get(0).getSubject();
	}

	long getTimestamp() {
		return headers.get(0).getTimestamp();
	}

	boolean getRead() {
		return read;
	}

	boolean getStarred() {
		return starred;
	}

	int getLength() {
		return headers.size();
	}

	private static class HeaderComparator
	implements Comparator<PrivateMessageHeader> {

		public int compare(PrivateMessageHeader a, PrivateMessageHeader b) {
			// The newest message comes first
			long aTime = a.getTimestamp(), bTime = b.getTimestamp();
			if(aTime > bTime) return -1;
			if(aTime < bTime) return 1;
			return 0;
		}
	}

	private static class ItemComparator
	implements Comparator<ConversationListItem> {

		public int compare(ConversationListItem a, ConversationListItem b) {
			// The item with the newest message comes first
			return HEADER_COMPARATOR.compare(a.headers.get(0),
					b.headers.get(0));
		}
	}
}
