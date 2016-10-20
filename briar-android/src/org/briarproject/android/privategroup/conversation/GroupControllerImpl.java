package org.briarproject.android.privategroup.conversation;

import android.support.annotation.Nullable;

import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.threaded.ThreadListControllerImpl;
import org.briarproject.api.clients.MessageTracker.GroupCount;
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
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

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
			AndroidNotificationManager notificationManager, Clock clock) {
		super(dbExecutor, lifecycleManager, identityManager, cryptoExecutor,
				eventBus, notificationManager, clock);
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
	protected String loadMessageBody(MessageId id) throws DbException {
		return privateGroupManager.getMessageBody(id);
	}

	@Override
	protected void markRead(MessageId id) throws DbException {
		privateGroupManager.setReadFlag(getGroupId(), id, true);
	}

	@Override
	protected long getLatestTimestamp() throws DbException {
		GroupCount count = privateGroupManager.getGroupCount(getGroupId());
		return count.getLatestMsgTime();
	}

	@Override
	protected GroupMessage createLocalMessage(String body, long timestamp,
			@Nullable MessageId parentId, LocalAuthor author) {
		MessageId previousMsgId = null; // TODO
		return groupMessageFactory
				.createGroupMessage(getGroupId(), timestamp, parentId,
						author, body, previousMsgId);
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
		return new GroupMessageItem(header, body);
	}

}
