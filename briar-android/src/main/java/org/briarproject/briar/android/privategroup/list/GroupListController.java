package org.briarproject.briar.android.privategroup.list;

import androidx.annotation.UiThread;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.android.controller.DbController;
import org.briarproject.briar.android.controller.handler.ExceptionHandler;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.api.privategroup.GroupMessageHeader;

import java.util.Collection;

@NotNullByDefault
interface GroupListController extends DbController {

	/**
	 * The listener must be set right after the controller was injected
	 */
	@UiThread
	void setGroupListListener(GroupListListener listener);

	@UiThread
	void onStart();

	@UiThread
	void onStop();

	void loadGroups(
			ResultExceptionHandler<Collection<GroupItem>, DbException> result);

	void removeGroup(GroupId g, ExceptionHandler<DbException> result);

	void loadAvailableGroups(
			ResultExceptionHandler<Integer, DbException> result);

	interface GroupListListener {

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
