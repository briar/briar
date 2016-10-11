package org.briarproject.android.privategroup.conversation;

import android.support.annotation.Nullable;

import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.threaded.ThreadListControllerImpl;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.GroupMessageAddedEvent;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.privategroup.GroupMessage;
import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

public class GroupControllerImpl
		extends ThreadListControllerImpl<PrivateGroup, GroupMessageItem, GroupMessageHeader, GroupMessage>
		implements GroupController {

	private static final Logger LOG =
			Logger.getLogger(GroupControllerImpl.class.getName());

	private final PrivateGroupManager privateGroupManager;

	@Inject
	GroupControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			@CryptoExecutor Executor cryptoExecutor,
			PrivateGroupManager privateGroupManager, EventBus eventBus,
			AndroidNotificationManager notificationManager) {
		super(dbExecutor, lifecycleManager, cryptoExecutor, eventBus,
				notificationManager);
		this.privateGroupManager = privateGroupManager;
	}

	@Override
	public void onActivityResume() {
		super.onActivityResume();
		notificationManager.clearForumPostNotification(groupId);
	}

	@Override
	public void eventOccurred(Event e) {
		super.eventOccurred(e);

		if (e instanceof GroupMessageAddedEvent) {
			final GroupMessageAddedEvent pe = (GroupMessageAddedEvent) e;
			if (!pe.isLocal() && pe.getGroupId().equals(groupId)) {
				LOG.info("Group message received, adding...");
				final GroupMessageHeader fph = pe.getHeader();
				listener.runOnUiThreadUnlessDestroyed(new Runnable() {
					@Override
					public void run() {
						listener.onHeaderReceived(fph);
					}
				});
			}
		}
	}

	@Override
	protected PrivateGroup loadGroupItem() throws DbException {
		return privateGroupManager.getPrivateGroup(groupId);
	}

	@Override
	protected Collection<GroupMessageHeader> loadHeaders() throws DbException {
		return privateGroupManager.getHeaders(groupId);
	}

	@Override
	protected void loadBodies(Collection<GroupMessageHeader> headers)
			throws DbException {
		for (GroupMessageHeader header : headers) {
			if (!bodyCache.containsKey(header.getId())) {
				String body =
						privateGroupManager.getMessageBody(header.getId());
				bodyCache.put(header.getId(), body);
			}
		}
	}

	@Override
	protected void markRead(MessageId id) throws DbException {
		privateGroupManager.setReadFlag(groupId, id, true);
	}

	@Override
	protected GroupMessage createLocalMessage(GroupId g, String body,
			@Nullable MessageId parentId) throws DbException {
		return privateGroupManager.createLocalMessage(groupId, body, parentId);
	}

	@Override
	protected GroupMessageHeader addLocalMessage(GroupMessage message)
			throws DbException {
		return privateGroupManager.addLocalMessage(message);
	}

	@Override
	protected void deleteGroupItem(PrivateGroup group) throws DbException {
		privateGroupManager.removePrivateGroup(group.getId());
	}

	@Override
	protected GroupMessageItem buildItem(GroupMessageHeader header) {
		return new GroupMessageItem(header, bodyCache.get(header.getId()));
	}

}
