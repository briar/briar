package org.briarproject.android.forum;

import java.util.Comparator;

class AvailableForumsItemComparator implements Comparator<AvailableForumsItem> {

	static final AvailableForumsItemComparator INSTANCE =
			new AvailableForumsItemComparator();

	public int compare(AvailableForumsItem a, AvailableForumsItem b) {
		if (a == b) return 0;
		String aName = a.getGroup().getName();
		String bName = b.getGroup().getName();
		return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
	}
}
