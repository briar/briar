package org.briarproject.android.groups;

import java.util.Comparator;

class AvailableGroupsItemComparator implements Comparator<AvailableGroupsItem> {

	static final AvailableGroupsItemComparator INSTANCE =
			new AvailableGroupsItemComparator();

	public int compare(AvailableGroupsItem a, AvailableGroupsItem b) {
		if (a == b) return 0;
		String aName = a.getGroup().getName();
		String bName = b.getGroup().getName();
		return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
	}
}
