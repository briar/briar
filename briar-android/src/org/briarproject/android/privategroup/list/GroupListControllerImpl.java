package org.briarproject.android.privategroup.list;

import android.support.annotation.CallSuper;

import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchGroupException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.GroupAddedEvent;
import org.briarproject.api.event.GroupDissolvedEvent;
import org.briarproject.api.event.GroupInvitationReceivedEvent;
import org.briarproject.api.event.GroupMessageAddedEvent;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.privategroup.invitation.GroupInvitationManager;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.privategroup.PrivateGroupManager.CLIENT_ID;

public class GroupListControllerImpl extends DbControllerImpl
		implements GroupListController, EventListener {

	private static final Logger LOG =
			Logger.getLogger(GroupListControllerImpl.class.getName());

	private final PrivateGroupManager groupManager;
	private final GroupInvitationManager groupInvitationManager;
	private final AndroidNotificationManager notificationManager;
	private final EventBus eventBus;

	protected volatile GroupListListener listener;

	@Inject
	GroupListControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, PrivateGroupManager groupManager,
			GroupInvitationManager groupInvitationManager,
			AndroidNotificationManager notificationManager, EventBus eventBus) {
		super(dbExecutor, lifecycleManager);
		this.groupManager = groupManager;
		this.groupInvitationManager = groupInvitationManager;
		this.notificationManager = notificationManager;
		this.eventBus = eventBus;
	}

	@Override
	public void setGroupListListener(GroupListListener listener) {
		this.listener = listener;
	}

	@Override
	@CallSuper
	public void onStart() {
		if (listener == null)
			throw new IllegalStateException(
					"GroupListListener needs to be attached");
		eventBus.addListener(this);
		notificationManager.blockAllGroupMessageNotifications();
		notificationManager.clearAllGroupMessageNotifications();
	}

	@Override
	@CallSuper
	public void onStop() {
		eventBus.removeListener(this);
		notificationManager.unblockAllGroupMessageNotifications();
	}

	@Override
	@CallSuper
	public void eventOccurred(Event e) {
		if (e instanceof GroupMessageAddedEvent) {
			GroupMessageAddedEvent g = (GroupMessageAddedEvent) e;
			LOG.info("Private group message added");
			onGroupMessageAdded(g.getHeader());
		} else if (e instanceof GroupInvitationReceivedEvent) {
			GroupInvitationReceivedEvent g = (GroupInvitationReceivedEvent) e;
			LOG.info("Private group invitation received");
			onGroupInvitationReceived();
		} else if (e instanceof GroupAddedEvent) {
			GroupAddedEvent g = (GroupAddedEvent) e;
			ClientId id = g.getGroup().getClientId();
			if (id.equals(CLIENT_ID)) {
				LOG.info("Private group added");
				onGroupAdded(g.getGroup().getId());
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent g = (GroupRemovedEvent) e;
			ClientId id = g.getGroup().getClientId();
			if (id.equals(CLIENT_ID)) {
				LOG.info("Private group removed");
				onGroupRemoved(g.getGroup().getId());
			}
		} else if (e instanceof GroupDissolvedEvent) {
			GroupDissolvedEvent g = (GroupDissolvedEvent) e;
			LOG.info("Private group dissolved");
			onGroupDissolved(g.getGroupId());
		}
	}

	private void onGroupMessageAdded(final GroupMessageHeader h) {
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				listener.onGroupMessageAdded(h);
			}
		});
	}

	private void onGroupInvitationReceived() {
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				listener.onGroupInvitationReceived();
			}
		});
	}

	private void onGroupAdded(final GroupId g) {
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				listener.onGroupAdded(g);
			}
		});
	}

	private void onGroupRemoved(final GroupId g) {
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				listener.onGroupRemoved(g);
			}
		});
	}

	private void onGroupDissolved(final GroupId g) {
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				listener.onGroupDissolved(g);
			}
		});
	}

	@Override
	public void loadGroups(
			final ResultExceptionHandler<Collection<GroupItem>, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Collection<PrivateGroup> groups =
							groupManager.getPrivateGroups();
					List<GroupItem> items = new ArrayList<>(groups.size());
					for (PrivateGroup g : groups) {
						try {
							GroupId id = g.getId();
							GroupCount count = groupManager.getGroupCount(id);
							boolean dissolved = groupManager.isDissolved(id);
							items.add(new GroupItem(g, count, dissolved));
						} catch (NoSuchGroupException e) {
							// Continue
						}
					}
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading groups took " + duration + " ms");
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
				try {
					long now = System.currentTimeMillis();
					groupManager.removePrivateGroup(g);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Removing group took " + duration + " ms");
					handler.onResult(null);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	@Override
	public void loadAvailableGroups(
			final ResultExceptionHandler<Integer, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					handler.onResult(
							groupInvitationManager.getInvitations().size());
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

}
