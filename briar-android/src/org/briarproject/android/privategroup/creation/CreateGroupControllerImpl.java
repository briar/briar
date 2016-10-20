package org.briarproject.android.privategroup.creation;

import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.privategroup.GroupMessage;
import org.briarproject.api.privategroup.GroupMessageFactory;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.system.Clock;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;

public class CreateGroupControllerImpl extends DbControllerImpl
		implements CreateGroupController {

	private static final Logger LOG =
			Logger.getLogger(CreateGroupControllerImpl.class.getName());

	private final IdentityManager identityManager;
	private final PrivateGroupFactory groupFactory;
	private final GroupMessageFactory groupMessageFactory;
	private final PrivateGroupManager groupManager;
	private final Clock clock;
	@CryptoExecutor
	private final Executor cryptoExecutor;

	@Inject
	CreateGroupControllerImpl(@DatabaseExecutor Executor dbExecutor,
			@CryptoExecutor Executor cryptoExecutor,
			LifecycleManager lifecycleManager, IdentityManager identityManager,
			PrivateGroupFactory groupFactory,
			GroupMessageFactory groupMessageFactory,
			PrivateGroupManager groupManager, Clock clock) {
		super(dbExecutor, lifecycleManager);
		this.identityManager = identityManager;
		this.groupFactory = groupFactory;
		this.groupMessageFactory = groupMessageFactory;
		this.groupManager = groupManager;
		this.clock = clock;
		this.cryptoExecutor = cryptoExecutor;
	}

	@Override
	public void createGroup(final String name,
			final ResultExceptionHandler<GroupId, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					LocalAuthor author = identityManager.getLocalAuthor();
					createGroupAndMessages(author, name, handler);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	private void createGroupAndMessages(final LocalAuthor author,
			final String name,
			final ResultExceptionHandler<GroupId, DbException> handler) {
		cryptoExecutor.execute(new Runnable() {
			@Override
			public void run() {
				LOG.info("Creating group...");
				PrivateGroup group =
						groupFactory.createPrivateGroup(name, author);
				LOG.info("Creating new member announcement...");
				GroupMessage newMemberMsg = groupMessageFactory
						.createNewMemberMessage(group.getId(),
								clock.currentTimeMillis(), author, author);
				LOG.info("Creating new join announcement...");
				GroupMessage joinMsg = groupMessageFactory
						.createJoinMessage(group.getId(),
								newMemberMsg.getMessage().getTimestamp(),
								author, newMemberMsg.getMessage().getId());
				storeGroup(group, newMemberMsg, joinMsg, handler);
			}
		});
	}

	private void storeGroup(final PrivateGroup group,
			final GroupMessage newMemberMsg, final GroupMessage joinMsg,
			final ResultExceptionHandler<GroupId, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				LOG.info("Adding group to database...");
				try {
					groupManager.addPrivateGroup(group, newMemberMsg, joinMsg);
					handler.onResult(group.getId());
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
