package org.briarproject.android.groups;

import static org.briarproject.android.groups.GroupItem.NEW;

import java.util.Comparator;

class GroupItemComparator implements Comparator<GroupItem> {

	static final GroupItemComparator INSTANCE = new GroupItemComparator();

	public int compare(GroupItem a, GroupItem b) {
		if(a == b) return 0;
		if(a == NEW) return 1;
		if(b == NEW) return -1;
		String aName = a.getGroup().getName(), bName = b.getGroup().getName();
		return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
	}
}
