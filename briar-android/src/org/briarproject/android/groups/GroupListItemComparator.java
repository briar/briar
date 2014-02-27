package org.briarproject.android.groups;

import java.util.Comparator;

class GroupListItemComparator implements Comparator<GroupListItem> {

	static final GroupListItemComparator INSTANCE =
			new GroupListItemComparator();

	public int compare(GroupListItem a, GroupListItem b) {
		if(a == b) return 0;
		// The item with the newest message comes first
		long aTime = a.getTimestamp(), bTime = b.getTimestamp();
		if(aTime > bTime) return -1;
		if(aTime < bTime) return 1;
		// Break ties by group name
		String aName = a.getGroup().getName();
		String bName = b.getGroup().getName();
		return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
	}
}
