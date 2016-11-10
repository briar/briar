package org.briarproject.android.contactselection;

import org.briarproject.android.controller.DbController;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

@NotNullByDefault
public interface ContactSelectorController<I extends SelectableContactItem>
		extends DbController {

	void loadContacts(GroupId g, Collection<ContactId> selection,
			ResultExceptionHandler<Collection<I>, DbException> handler);

}
