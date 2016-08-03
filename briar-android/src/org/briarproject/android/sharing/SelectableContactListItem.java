package org.briarproject.android.sharing;

import org.briarproject.android.contact.ContactListItem;
import org.briarproject.android.contact.ConversationItem;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;

import java.util.Collections;

// This class is not thread-safe
public class SelectableContactListItem extends ContactListItem {

	private boolean selected, disabled;

	public SelectableContactListItem(Contact contact, LocalAuthor localAuthor,
			GroupId groupId, boolean selected, boolean disabled) {

		super(contact, localAuthor, false, groupId,
				Collections.<ConversationItem>emptyList());

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
