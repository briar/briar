package net.sf.briar.android.messages;

import java.util.Comparator;

import net.sf.briar.api.db.PrivateMessageHeader;

class DescendingHeaderComparator implements Comparator<PrivateMessageHeader> {

	static final DescendingHeaderComparator INSTANCE =
			new DescendingHeaderComparator();

	public int compare(PrivateMessageHeader a, PrivateMessageHeader b) {
		// The newest message comes first
		long aTime = a.getTimestamp(), bTime = b.getTimestamp();
		if(aTime > bTime) return -1;
		if(aTime < bTime) return 1;
		return 0;
	}
}