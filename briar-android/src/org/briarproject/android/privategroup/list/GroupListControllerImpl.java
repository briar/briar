package org.briarproject.android.privategroup.list;

import android.support.annotation.CallSuper;

import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.GroupAddedEvent;
import org.briarproject.api.event.GroupMessageAddedEvent;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;

public class GroupListControllerImpl extends DbControllerImpl
		implements GroupListController, EventListener {

	private static final Logger LOG =
			Logger.getLogger(GroupListControllerImpl.class.getName());

	@Inject
	PrivateGroupManager groupManager;
	@Inject
	EventBus eventBus;
	@Inject
	AndroidNotificationManager notificationManager;
	@Inject
	IdentityManager identityManager;

	protected volatile GroupListListener listener;

	@Inject
	GroupListControllerImpl() {

	}

	@Override
	public void setGroupListListener(GroupListListener listener) {
		this.listener = listener;
	}

	@CallSuper
	public void onStart() {
		if (listener == null)
			throw new IllegalStateException(
					"GroupListListener needs to be attached");
		eventBus.addListener(this);
	}

	@CallSuper
	public void onStop() {
		eventBus.removeListener(this);
	}

	@Override
	@CallSuper
	public void eventOccurred(Event e) {
		if (e instanceof GroupMessageAddedEvent) {
			final GroupMessageAddedEvent m = (GroupMessageAddedEvent) e;
			LOG.info("New group message added");
			listener.runOnUiThreadUnlessDestroyed(new Runnable() {
				@Override
				public void run() {
					listener.onGroupMessageAdded(m.getHeader());
				}
			});
		} else if (e instanceof GroupAddedEvent) {
			final GroupAddedEvent gae = (GroupAddedEvent) e;
			ClientId id = gae.getGroup().getClientId();
			if (id.equals(groupManager.getClientId())) {
				LOG.info("Private group added");
				listener.runOnUiThreadUnlessDestroyed(new Runnable() {
					@Override
					public void run() {
						listener.onGroupAdded(gae.getGroup().getId());
					}
				});
			}
		} else if (e instanceof GroupRemovedEvent) {
			final GroupRemovedEvent gre = (GroupRemovedEvent) e;
			ClientId id = gre.getGroup().getClientId();
			if (id.equals(groupManager.getClientId())) {
				LOG.info("Private group removed");
				listener.runOnUiThreadUnlessDestroyed(new Runnable() {
					@Override
					public void run() {
						listener.onGroupRemoved(gre.getGroup().getId());
					}
				});
			}
		}
	}

	@Override
	public void loadGroups(
			final ResultExceptionHandler<Collection<GroupItem>, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				LOG.info("Loading groups from database...");
				try {
					Collection<PrivateGroup> groups =
							groupManager.getPrivateGroups();
					List<GroupItem> items = new ArrayList<>(groups.size());
					for (PrivateGroup g : groups) {
						GroupCount c = groupManager.getGroupCount(g.getId());
						boolean dissolved = groupManager.isDissolved(g.getId());
						items.add(new GroupItem(g, c.getMsgCount(),
								c.getLatestMsgTime(), c.getUnreadCount(),
								dissolved));
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

	@Override
	public void removeGroup(final GroupId g,
			final ResultExceptionHandler<Void, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				LOG.info("Removing group from database...");
				try {
					groupManager.removePrivateGroup(g);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

}
