package net.sf.briar.android.contact;

import java.util.Comparator;

class ContactComparator implements Comparator<ContactListItem> {

	static final ContactComparator INSTANCE = new ContactComparator();

	public int compare(ContactListItem a, ContactListItem b) {
		return String.CASE_INSENSITIVE_ORDER.compare(a.getContactName(),
				b.getContactName());
	}
}