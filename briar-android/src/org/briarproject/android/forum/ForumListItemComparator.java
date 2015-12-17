package org.briarproject.android.forum;

import java.util.Comparator;

class ForumListItemComparator implements Comparator<ForumListItem> {

	static final ForumListItemComparator INSTANCE =
			new ForumListItemComparator();

	public int compare(ForumListItem a, ForumListItem b) {
		if (a == b) return 0;
		// The forum with the newest message comes first
		long aTime = a.getTimestamp(), bTime = b.getTimestamp();
		if (aTime > bTime) return -1;
		if (aTime < bTime) return 1;
		// Break ties by forum name
		String aName = a.getForum().getName();
		String bName = b.getForum().getName();
		return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
	}
}
