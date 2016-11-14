package org.briarproject.android.privategroup.reveal;

import org.briarproject.android.contactselection.SelectableContactItem;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.privategroup.Visibility;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
public class RevealableContactItem extends SelectableContactItem {

	private final Visibility visibility;

	public RevealableContactItem(Contact contact, boolean selected,
			boolean disabled, Visibility visibility) {
		super(contact, selected, disabled);
		this.visibility = visibility;
	}

	public Visibility getVisibility() {
		return visibility;
	}

}
