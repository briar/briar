package net.sf.briar.android.contact;

import java.util.Comparator;

public class ConversationItemComparator
implements Comparator<ConversationItem> {

	public static final ConversationItemComparator INSTANCE =
			new ConversationItemComparator();

	public int compare(ConversationItem a, ConversationItem b) {
		// The oldest message comes first
		long aTime = a.getHeader().getTimestamp();
		long bTime = b.getHeader().getTimestamp();
		if(aTime < bTime) return -1;
		if(aTime > bTime) return 1;
		return 0;
	}
}
