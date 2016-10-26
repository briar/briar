package org.briarproject.android.privategroup.creation;

import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;

public class CreateGroupControllerImpl extends DbControllerImpl
		implements CreateGroupController {

	private static final Logger LOG =
			Logger.getLogger(CreateGroupControllerImpl.class.getName());

	private final PrivateGroupManager groupManager;

	@Inject
	CreateGroupControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			PrivateGroupManager groupManager) {
		super(dbExecutor, lifecycleManager);
		this.groupManager = groupManager;
	}

	@Override
	public void createGroup(final String name,
			final ResultExceptionHandler<GroupId, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				LOG.info("Adding group to database...");
				try {
					handler.onResult(groupManager.addPrivateGroup(name));
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	@Override
	public void sendInvitation(final GroupId groupId,
			final Collection<ContactId> contacts, final String message,
			final ResultExceptionHandler<Void, DbException> result) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				// TODO actually send invitation
				//noinspection ConstantConditions
				result.onResult(null);
			}
		});
	}

}
