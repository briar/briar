package net.sf.briar.android.blogs;

import static net.sf.briar.android.blogs.LocalGroupItem.NEW;

import java.util.Comparator;

class LocalGroupItemComparator implements Comparator<LocalGroupItem> {

	static final LocalGroupItemComparator INSTANCE =
			new LocalGroupItemComparator();

	public int compare(LocalGroupItem a, LocalGroupItem b) {
		if(a == b) return 0;
		if(a == NEW) return 1;
		if(b == NEW) return -1;
		String aName = a.getLocalGroup().getName();
		String bName = b.getLocalGroup().getName();
		return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
	}
}
