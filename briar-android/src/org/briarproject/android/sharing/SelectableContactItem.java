package org.briarproject.android.sharing;

import org.briarproject.android.contact.ContactItem;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
public class SelectableContactItem extends ContactItem {

	private boolean selected, disabled;

	public SelectableContactItem(Contact contact, boolean connected,
			boolean selected, boolean disabled) {
		super(contact, connected);
		this.selected = selected;
		this.disabled = disabled;
	}

	public boolean isSelected() {
		return selected;
	}

	public void toggleSelected() {
		selected = !selected;
	}

	public boolean isDisabled() {
		return disabled;
	}

}
