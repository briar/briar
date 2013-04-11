package net.sf.briar.android.identity;

import static net.sf.briar.android.identity.LocalAuthorItem.ANONYMOUS;
import static net.sf.briar.android.identity.LocalAuthorItem.NEW;

import java.util.Comparator;

public class LocalAuthorItemComparator implements Comparator<LocalAuthorItem> {

	public static final LocalAuthorItemComparator INSTANCE =
			new LocalAuthorItemComparator();

	public int compare(LocalAuthorItem a, LocalAuthorItem b) {
		if(a == b) return 0;
		if(a == ANONYMOUS || b == NEW) return -1;
		if(a == NEW || b == ANONYMOUS) return 1;
		String aName = a.getLocalAuthor().getName();
		String bName = b.getLocalAuthor().getName();
		return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
	}
}
