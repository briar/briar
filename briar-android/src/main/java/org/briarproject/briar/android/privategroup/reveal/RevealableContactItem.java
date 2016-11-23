package org.briarproject.briar.android.privategroup.reveal;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.contactselection.SelectableContactItem;
import org.briarproject.briar.api.privategroup.Visibility;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class RevealableContactItem extends SelectableContactItem {

	private final Visibility visibility;

	RevealableContactItem(Contact contact, boolean selected,
			boolean disabled, Visibility visibility) {
		super(contact, selected, disabled);
		this.visibility = visibility;
	}

	Visibility getVisibility() {
		return visibility;
	}

}
