package org.briarproject.android.groups;

import static org.briarproject.android.groups.ManageGroupsItem.NONE;

import java.util.Comparator;

class ManageGroupsItemComparator implements Comparator<ManageGroupsItem> {

	static final ManageGroupsItemComparator INSTANCE =
			new ManageGroupsItemComparator();

	public int compare(ManageGroupsItem a, ManageGroupsItem b) {
		if(a == b) return 0;
		if(a == NONE) return 1;
		if(b == NONE) return -1;
		String aName = a.getGroupStatus().getGroup().getName();
		String bName = b.getGroupStatus().getGroup().getName();
		return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
	}
}
