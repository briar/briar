package org.briarproject.android.privategroup.creation;

import org.briarproject.android.controller.DbController;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

public interface CreateGroupController extends DbController {

	void createGroup(String name,
			ResultExceptionHandler<GroupId, DbException> result);

	void sendInvitation(GroupId groupId, Collection<ContactId> contacts,
			String message, ResultExceptionHandler<Void, DbException> result);

}
