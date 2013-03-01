package net.sf.briar.android.contact;

import java.util.Comparator;

import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;

// This class is not thread-safe
class ContactListItem {

	static Comparator<ContactListItem> COMPARATOR = new ItemComparator();

	private final Contact contact;
	private boolean connected;

	ContactListItem(Contact contact, boolean connected) {
		this.contact = contact;
		this.connected = connected;
	}

	ContactId getContactId() {
		return contact.getId();
	}

	String getName() {
		return contact.getName();
	}

	long getLastConnected() {
		return contact.getLastConnected();
	}

	boolean isConnected() {
		return connected;
	}

	void setConnected(boolean connected) {
		this.connected = connected;
	}

	private static class ItemComparator implements Comparator<ContactListItem> {

		public int compare(ContactListItem a, ContactListItem b) {
			return String.CASE_INSENSITIVE_ORDER.compare(a.contact.getName(),
					b.contact.getName());
		}
	}
}