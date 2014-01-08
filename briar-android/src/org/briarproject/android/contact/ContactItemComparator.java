package org.briarproject.android.contact;

import java.util.Comparator;

public class ContactItemComparator implements Comparator<ContactItem> {

	public static final ContactItemComparator INSTANCE =
			new ContactItemComparator();

	public int compare(ContactItem a, ContactItem b) {
		if(a == b) return 0;
		if(a == ContactItem.NEW) return 1;
		if(b == ContactItem.NEW) return -1;
		String aName = a.getContact().getAuthor().getName();
		String bName = b.getContact().getAuthor().getName();
		return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
	}
}
