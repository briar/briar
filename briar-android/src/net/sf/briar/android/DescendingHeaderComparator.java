package net.sf.briar.android;

import java.util.Comparator;

import net.sf.briar.api.db.MessageHeader;

public class DescendingHeaderComparator implements Comparator<MessageHeader> {

	public static final DescendingHeaderComparator INSTANCE =
			new DescendingHeaderComparator();

	public int compare(MessageHeader a, MessageHeader b) {
		// The newest message comes first
		long aTime = a.getTimestamp(), bTime = b.getTimestamp();
		if(aTime > bTime) return -1;
		if(aTime < bTime) return 1;
		return 0;
	}
}