package org.briarproject.android.sharing;

import android.support.annotation.UiThread;

import org.briarproject.android.contact.ContactListItem;
import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;

// This class is NOT thread-safe
public class SelectableContactListItem extends ContactListItem {

	private boolean selected, disabled;

	public SelectableContactListItem(Contact contact, LocalAuthor localAuthor,
			GroupId groupId, boolean selected, boolean disabled) {

		super(contact, localAuthor, false, groupId, new GroupCount(0, 0, 0));

		this.selected = selected;
		this.disabled = disabled;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
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
