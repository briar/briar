package org.briarproject.android.forum;

import java.util.Comparator;

class ForumItemComparator implements Comparator<ForumItem> {

	static final ForumItemComparator INSTANCE = new ForumItemComparator();

	public int compare(ForumItem a, ForumItem b) {
		// The oldest message comes first
		long aTime = a.getHeader().getTimestamp();
		long bTime = b.getHeader().getTimestamp();
		if (aTime < bTime) return -1;
		if (aTime > bTime) return 1;
		return 0;
	}
}
