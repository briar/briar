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
import org.briarproject.api.sync.MessageId;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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
		notificationManager.clearForumPostNotification(getGroupId());
	}

	@Override
	public void eventOccurred(Event e) {
		super.eventOccurred(e);

		if (e instanceof GroupMessageAddedEvent) {
			final GroupMessageAddedEvent gmae = (GroupMessageAddedEvent) e;
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
	protected PrivateGroup loadGroupItem() throws DbException {
		return privateGroupManager.getPrivateGroup(getGroupId());
	}

	@Override
	protected Collection<GroupMessageHeader> loadHeaders() throws DbException {
		return privateGroupManager.getHeaders(getGroupId());
	}

	@Override
	protected Map<MessageId, String> loadBodies(
			Collection<GroupMessageHeader> headers)
			throws DbException {
		Map<MessageId, String> bodies = new HashMap<>();
		for (GroupMessageHeader header : headers) {
			if (!bodyCache.containsKey(header.getId())) {
				String body =
						privateGroupManager.getMessageBody(header.getId());
				bodies.put(header.getId(), body);
			}
		}
		return bodies;
	}

	@Override
	protected void markRead(MessageId id) throws DbException {
		privateGroupManager.setReadFlag(getGroupId(), id, true);
	}

	@Override
	protected GroupMessage createLocalMessage(String body,
			@Nullable MessageId parentId) throws DbException {
		return privateGroupManager
				.createLocalMessage(getGroupId(), body, parentId);
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
	protected GroupMessageItem buildItem(GroupMessageHeader header,
			String body) {
		return new GroupMessageItem(header, body);
	}

}
