package org.briarproject.android.privategroup.conversation;

import android.support.annotation.UiThread;

import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.android.threaded.ThreadListController;
import org.briarproject.api.db.DbException;
import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.privategroup.PrivateGroup;

public interface GroupController
		extends
		ThreadListController<PrivateGroup, GroupMessageItem, GroupMessageHeader> {

	void isCreator(PrivateGroup group,
			ResultExceptionHandler<Boolean, DbException> handler);

	void isDissolved(
			ResultExceptionHandler<Boolean, DbException> handler);

	interface GroupListener extends ThreadListListener<GroupMessageHeader> {
		@UiThread
		void onGroupDissolved();
	}

}
