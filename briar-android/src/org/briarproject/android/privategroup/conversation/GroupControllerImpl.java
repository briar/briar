package org.briarproject.android.privategroup.conversation;

import android.support.annotation.Nullable;

import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.android.threaded.ThreadListControllerImpl;
import org.briarproject.api.FormatException;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.GroupMessageAddedEvent;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.privategroup.GroupMessage;
import org.briarproject.api.privategroup.GroupMessageFactory;
import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.sync.MessageId;

import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.identity.Author.Status.OURSELVES;

public class GroupControllerImpl
		extends ThreadListControllerImpl<PrivateGroup, GroupMessageItem, GroupMessageHeader>
		implements GroupController {

	private static final Logger LOG =
			Logger.getLogger(GroupControllerImpl.class.getName());

	@Inject
	volatile GroupMessageFactory groupMessageFactory;
	@Inject
	volatile PrivateGroupManager privateGroupManager;

	@Inject
	GroupControllerImpl() {
		super();
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
				updateNewestTimestamp(fph.getTimestamp());
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
	public void send(final String body, @Nullable final MessageId parentId,
			final ResultExceptionHandler<GroupMessageItem, DbException> handler) {
		cryptoExecutor.execute(new Runnable() {
			@Override
			public void run() {
				LOG.info("Create message...");
				long timestamp = System.currentTimeMillis();
				timestamp = Math.max(timestamp, newestTimeStamp.get());
				GroupMessage gm;
				try {
					LocalAuthor a = identityManager.getLocalAuthor();
					gm = groupMessageFactory.createGroupMessage(groupId,
							timestamp, parentId, a, body);
				} catch (GeneralSecurityException | FormatException e) {
					throw new RuntimeException(e);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
					return;
				}
				bodyCache.put(gm.getMessage().getId(), body);
				storeMessage(gm, handler);
			}
		});
	}

	private void storeMessage(final GroupMessage gm,
			final ResultExceptionHandler<GroupMessageItem, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					LOG.info("Store message...");
					long now = System.currentTimeMillis();
					privateGroupManager.addLocalMessage(gm);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Storing message took " + duration + " ms");

					GroupMessageHeader h = new GroupMessageHeader(groupId,
							gm.getMessage().getId(), gm.getParent(),
							gm.getMessage().getTimestamp(), gm.getAuthor(),
							OURSELVES, true);

					handler.onResult(buildItem(h));
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
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
