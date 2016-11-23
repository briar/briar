package org.briarproject.briar.android.contactselection;

import android.support.annotation.UiThread;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.DestroyableContext;

import java.util.Collection;

@NotNullByDefault
public interface ContactSelectorListener extends DestroyableContext {

	@UiThread
	void contactsSelected(Collection<ContactId> contacts);

}
