package org.briarproject.android.contactselection;

import org.briarproject.android.contact.ContactItem;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
public class SelectableContactItem extends ContactItem {

	private boolean selected, disabled;

	public SelectableContactItem(Contact contact, boolean selected,
			boolean disabled) {
		super(contact);
		this.selected = selected;
		this.disabled = disabled;
	}

	boolean isSelected() {
		return selected;
	}

	void toggleSelected() {
		selected = !selected;
	}

	public boolean isDisabled() {
		return disabled;
	}

}
