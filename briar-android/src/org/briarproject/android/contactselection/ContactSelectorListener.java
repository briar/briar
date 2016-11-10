package org.briarproject.android.contactselection;

import android.support.annotation.UiThread;

import org.briarproject.android.DestroyableContext;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.nullsafety.NotNullByDefault;

import java.util.Collection;

@NotNullByDefault
interface ContactSelectorListener<I extends SelectableContactItem>
		extends DestroyableContext {

	@UiThread
	void contactsSelected(Collection<ContactId> contacts);

}
