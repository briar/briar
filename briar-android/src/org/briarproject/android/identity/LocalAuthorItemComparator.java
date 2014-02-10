package org.briarproject.android.identity;

import static org.briarproject.android.identity.LocalAuthorItem.ANONYMOUS;
import static org.briarproject.android.identity.LocalAuthorItem.NEW;

import java.util.Comparator;

public class LocalAuthorItemComparator implements Comparator<LocalAuthorItem> {

	public static final LocalAuthorItemComparator INSTANCE =
			new LocalAuthorItemComparator();

	public int compare(LocalAuthorItem a, LocalAuthorItem b) {
		if(a == b) return 0;
		// NEW comes after everything else
		if(a == NEW) return 1;
		if(b == NEW) return -1;
		// ANONYMOUS comes after everything else except NEW
		if(a == ANONYMOUS) return 1;
		if(b == ANONYMOUS) return -1;
		// Sort items in order of creation, so the oldest item is the default
		long aCreated = a.getLocalAuthor().getTimeCreated();
		long bCreated = b.getLocalAuthor().getTimeCreated();
		if(aCreated < bCreated) return -1;
		if(aCreated > bCreated) return 1;
		return 0;
	}
}
