package org.briarproject.android.groups;

import java.util.Comparator;

class GroupItemComparator implements Comparator<GroupItem> {

	static final GroupItemComparator INSTANCE = new GroupItemComparator();

	public int compare(GroupItem a, GroupItem b) {
		// The oldest message comes first
		long aTime = a.getHeader().getTimestamp();
		long bTime = b.getHeader().getTimestamp();
		if (aTime < bTime) return -1;
		if (aTime > bTime) return 1;
		return 0;
	}
}
