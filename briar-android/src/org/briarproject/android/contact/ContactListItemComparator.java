package org.briarproject.android.contact;

import java.util.Comparator;

class ContactListItemComparator implements Comparator<ContactListItem> {

	static final ContactListItemComparator INSTANCE =
			new ContactListItemComparator();

	public int compare(ContactListItem a, ContactListItem b) {
		String aName = a.getContact().getAuthor().getName();
		String bName = b.getContact().getAuthor().getName();
		return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
	}
}
