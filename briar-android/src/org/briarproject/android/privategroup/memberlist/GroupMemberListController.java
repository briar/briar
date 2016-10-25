package org.briarproject.android.privategroup.memberlist;

import org.briarproject.android.controller.DbController;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

public interface GroupMemberListController extends DbController {

	void loadMembers(GroupId groupId,
			ResultExceptionHandler<Collection<MemberListItem>, DbException> handler);

}
