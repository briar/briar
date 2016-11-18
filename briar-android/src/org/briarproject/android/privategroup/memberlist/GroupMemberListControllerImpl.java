package org.briarproject.android.privategroup.memberlist;

import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.privategroup.GroupMember;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;

class GroupMemberListControllerImpl extends DbControllerImpl
		implements GroupMemberListController {

	private static final Logger LOG =
			Logger.getLogger(GroupMemberListControllerImpl.class.getName());

	private final PrivateGroupManager privateGroupManager;

	@Inject
	GroupMemberListControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			PrivateGroupManager privateGroupManager) {
		super(dbExecutor, lifecycleManager);
		this.privateGroupManager = privateGroupManager;
	}

	@Override
	public void loadMembers(final GroupId groupId, final
			ResultExceptionHandler<Collection<MemberListItem>, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					Collection<MemberListItem> items = new ArrayList<>();
					Collection<GroupMember> members =
							privateGroupManager.getMembers(groupId);
					for (GroupMember m : members) {
						items.add(new MemberListItem(m));
					}
					handler.onResult(items);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

}
