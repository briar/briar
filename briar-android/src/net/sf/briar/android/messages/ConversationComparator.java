package net.sf.briar.android.messages;

import java.util.Comparator;

class ConversationComparator implements Comparator<ConversationListItem> {

	static final ConversationComparator INSTANCE = new ConversationComparator();

	public int compare(ConversationListItem a, ConversationListItem b) {
		// The item with the newest message comes first
		long aTime = a.getTimestamp(), bTime = b.getTimestamp();
		if(aTime > bTime) return -1;
		if(aTime < bTime) return 1;
		return 0;
	}
}