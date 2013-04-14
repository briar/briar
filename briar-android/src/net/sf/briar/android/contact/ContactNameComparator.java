package net.sf.briar.android.contact;

import java.util.Comparator;

public class ContactNameComparator implements Comparator<ContactItem> {

	public static final ContactNameComparator INSTANCE =
			new ContactNameComparator();

	public int compare(ContactItem a, ContactItem b) {
		if(a == b) return 0;
		if(a == ContactItem.NEW) return 1;
		if(b == ContactItem.NEW) return -1;
		String aName = a.getContact().getAuthor().getName();
		String bName = b.getContact().getAuthor().getName();
		return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
	}
}
