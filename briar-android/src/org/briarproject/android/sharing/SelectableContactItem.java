package org.briarproject.android.sharing;

import org.briarproject.android.contact.ContactItem;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class SelectableContactItem extends ContactItem {

	private boolean selected, disabled;

	SelectableContactItem(Contact contact, boolean connected,
			boolean selected, boolean disabled) {
		super(contact, connected);
		this.selected = selected;
		this.disabled = disabled;
	}

	boolean isSelected() {
		return selected;
	}

	void toggleSelected() {
		selected = !selected;
	}

	boolean isDisabled() {
		return disabled;
	}

}
