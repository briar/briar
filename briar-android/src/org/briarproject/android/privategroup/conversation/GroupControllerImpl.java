package org.briarproject.android.privategroup.conversation;

import android.support.annotation.Nullable;

import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.android.threaded.ThreadListControllerImpl;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.GroupMessageAddedEvent;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.privategroup.GroupMessage;
import org.briarproject.api.privategroup.GroupMessageFactory;
import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.privategroup.JoinMessageHeader;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.lang.Math.max;
import static java.util.logging.Level.WARNING;

public class GroupControllerImpl extends
		ThreadListControllerImpl<PrivateGroup, GroupMessageItem, GroupMessageHeader, GroupMessage>
		implements GroupController {

	private static final Logger LOG =
			Logger.getLogger(GroupControllerImpl.class.getName());

	private final PrivateGroupManager privateGroupManager;
	private final GroupMessageFactory groupMessageFactory;

	@Inject
	GroupControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, IdentityManager identityManager,
			@CryptoExecutor Executor cryptoExecutor,
			PrivateGroupManager privateGroupManager,
			GroupMessageFactory groupMessageFactory, EventBus eventBus,
			Clock clock, AndroidNotificationManager notificationManager) {
		super(dbExecutor, lifecycleManager, identityManager, cryptoExecutor,
				eventBus, clock, notificationManager);
		this.privateGroupManager = privateGroupManager;
		this.groupMessageFactory = groupMessageFactory;
	}

	@Override
	public void onActivityStart() {
		super.onActivityStart();
		// TODO: Add new notification manager methods for private groups
	}

	@Override
	public void eventOccurred(Event e) {
		super.eventOccurred(e);

		if (e instanceof GroupMessageAddedEvent) {
			GroupMessageAddedEvent gmae = (GroupMessageAddedEvent) e;
			if (!gmae.isLocal() && gmae.getGroupId().equals(getGroupId())) {
				LOG.info("Group message received, adding...");
				final GroupMessageHeader h = gmae.getHeader();
				listener.runOnUiThreadUnlessDestroyed(new Runnable() {
					@Override
					public void run() {
						listener.onHeaderReceived(h);
					}
				});
			}
		}
	}

	@Override
	protected PrivateGroup loadNamedGroup() throws DbException {
		return privateGroupManager.getPrivateGroup(getGroupId());
	}

	@Override
	protected Collection<GroupMessageHeader> loadHeaders() throws DbException {
		return privateGroupManager.getHeaders(getGroupId());
	}

	@Override
	protected String loadMessageBody(GroupMessageHeader header)
			throws DbException {
		if (header instanceof JoinMessageHeader) {
			// will be looked up later
			return "";
		}
		return privateGroupManager.getMessageBody(header.getId());
	}

	@Override
	protected void markRead(MessageId id) throws DbException {
		privateGroupManager.setReadFlag(getGroupId(), id, true);
	}

	@Override
	public void createAndStoreMessage(final String body,
			@Nullable final GroupMessageItem parentItem,
			final ResultExceptionHandler<GroupMessageItem, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					LocalAuthor author = identityManager.getLocalAuthor();
					MessageId parentId = null;
					MessageId previousMsgId =
							privateGroupManager.getPreviousMsgId(getGroupId());
					// timestamp must be greater than the timestamps
					// of the member's previous message...
					long timestamp = privateGroupManager
							.getMessageTimestamp(previousMsgId);
					// ...and the parent post, if any
					if (parentItem != null) {
						timestamp = max(parentItem.getTimestamp(), timestamp);
						parentId = parentItem.getId();
					}
					timestamp = max(clock.currentTimeMillis(), timestamp + 1);
					createMessage(body, timestamp, parentId, author,
							previousMsgId, handler);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	private void createMessage(final String body, final long timestamp,
			final @Nullable MessageId parentId, final LocalAuthor author,
			final MessageId previousMsgId,
			final ResultExceptionHandler<GroupMessageItem, DbException> handler) {
		cryptoExecutor.execute(new Runnable() {
			@Override
			public void run() {
				LOG.info("Creating group message...");
				GroupMessage msg = groupMessageFactory
						.createGroupMessage(getGroupId(), timestamp,
								parentId, author, body, previousMsgId);
				storePost(msg, body, handler);
			}
		});
	}

	@Override
	protected GroupMessageHeader addLocalMessage(GroupMessage message)
			throws DbException {
		return privateGroupManager.addLocalMessage(message);
	}

	@Override
	protected void deleteNamedGroup(PrivateGroup group) throws DbException {
		privateGroupManager.removePrivateGroup(group.getId());
	}

	@Override
	protected GroupMessageItem buildItem(GroupMessageHeader header,
			String body) {
		if (header instanceof JoinMessageHeader) {
			return new JoinMessageItem(header, body);
		}
		return new GroupMessageItem(header, body);
	}

}
