package net.sf.briar.android.messages;

import java.util.Comparator;

import net.sf.briar.api.db.PrivateMessageHeader;

class AscendingHeaderComparator implements Comparator<PrivateMessageHeader> {

	static final AscendingHeaderComparator INSTANCE =
			new AscendingHeaderComparator();

	public int compare(PrivateMessageHeader a, PrivateMessageHeader b) {
		// The oldest message comes first
		long aTime = a.getTimestamp(), bTime = b.getTimestamp();
		if(aTime < bTime) return -1;
		if(aTime > bTime) return 1;
		return 0;
	}
}
