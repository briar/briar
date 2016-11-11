package org.briarproject.android.contactselection;

import android.support.annotation.UiThread;

import org.briarproject.android.DestroyableContext;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.nullsafety.NotNullByDefault;

import java.util.Collection;

@NotNullByDefault
public interface ContactSelectorListener extends DestroyableContext {

	@UiThread
	void contactsSelected(Collection<ContactId> contacts);

}
