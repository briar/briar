package org.briarproject.android.privategroup.list;

import android.support.annotation.UiThread;

import org.briarproject.android.DestroyableContext;
import org.briarproject.android.controller.DbController;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.db.DbException;
import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

public interface GroupListController extends DbController {

	/**
	 * The listener must be set right after the controller was injected
	 */
	void setGroupListListener(GroupListListener listener);

	@UiThread
	void onStart();

	@UiThread
	void onStop();

	void loadGroups(
			ResultExceptionHandler<Collection<GroupItem>, DbException> result);

	void removeGroup(GroupId g,
			ResultExceptionHandler<Void, DbException> result);

	void loadAvailableGroups(
			ResultExceptionHandler<Integer, DbException> result);

	interface GroupListListener extends DestroyableContext {

		@UiThread
		void onGroupMessageAdded(GroupMessageHeader header);

		@UiThread
		void onGroupInvitationReceived();

		@UiThread
		void onGroupAdded(GroupId groupId);

		@UiThread
		void onGroupRemoved(GroupId groupId);

		@UiThread
		void onGroupDissolved(GroupId groupId);

	}

}
