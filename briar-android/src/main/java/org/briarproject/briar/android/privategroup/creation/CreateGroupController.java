package org.briarproject.briar.android.privategroup.creation;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.android.contactselection.ContactSelectorController;
import org.briarproject.briar.android.contactselection.SelectableContactItem;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;

import java.util.Collection;

@NotNullByDefault
public interface CreateGroupController
		extends ContactSelectorController<SelectableContactItem> {

	void createGroup(String name,
			ResultExceptionHandler<GroupId, DbException> result);

	void sendInvitation(GroupId g, Collection<ContactId> contacts,
			String message, ResultExceptionHandler<Void, DbException> result);

}
