package org.briarproject.android.contactselection;

import android.support.annotation.UiThread;

import org.briarproject.android.DestroyableContext;
import org.briarproject.api.contact.ContactId;

import java.util.Collection;

interface ContactSelectorListener<I extends SelectableContactItem>
		extends DestroyableContext {

	ContactSelectorController<I> getController();

	@UiThread
	void contactsSelected(Collection<ContactId> contacts);

}
