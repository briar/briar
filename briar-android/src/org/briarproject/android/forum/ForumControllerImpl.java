package org.briarproject.android.forum;

import android.support.annotation.Nullable;

import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.android.threaded.ThreadListController.ThreadListListener;
import org.briarproject.android.threaded.ThreadListControllerImpl;
import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.ForumPostReceivedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.lang.Math.max;
import static java.util.logging.Level.WARNING;

public class ForumControllerImpl
		extends ThreadListControllerImpl<Forum, ForumItem, ForumPostHeader, ForumPost, ThreadListListener<ForumPostHeader>>
		implements ForumController {

	private static final Logger LOG =
			Logger.getLogger(ForumControllerImpl.class.getName());

	private final ForumManager forumManager;

	@Inject
	ForumControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, IdentityManager identityManager,
			@CryptoExecutor Executor cryptoExecutor,
			ForumManager forumManager, EventBus eventBus,
			Clock clock, AndroidNotificationManager notificationManager) {
		super(dbExecutor, lifecycleManager, identityManager, cryptoExecutor,
				eventBus, clock, notificationManager);
		this.forumManager = forumManager;
	}

	@Override
	public void onActivityStart() {
		super.onActivityStart();
		notificationManager.clearForumPostNotification(getGroupId());
	}

	@Override
	public void eventOccurred(Event e) {
		super.eventOccurred(e);

		if (e instanceof ForumPostReceivedEvent) {
			ForumPostReceivedEvent pe = (ForumPostReceivedEvent) e;
			if (pe.getGroupId().equals(getGroupId())) {
				LOG.info("Forum post received, adding...");
				final ForumPostHeader fph = pe.getForumPostHeader();
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
	protected Forum loadNamedGroup() throws DbException {
		return forumManager.getForum(getGroupId());
	}

	@Override
	protected Collection<ForumPostHeader> loadHeaders() throws DbException {
		return forumManager.getPostHeaders(getGroupId());
	}

	@Override
	protected String loadMessageBody(ForumPostHeader h) throws DbException {
		return forumManager.getPostBody(h.getId());
	}

	@Override
	protected void markRead(MessageId id) throws DbException {
		forumManager.setReadFlag(getGroupId(), id, true);
	}

	@Override
	public void createAndStoreMessage(final String body,
			@Nullable final ForumItem parentItem,
			final ResultExceptionHandler<ForumItem, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					LocalAuthor author = identityManager.getLocalAuthor();
					GroupCount count = forumManager.getGroupCount(getGroupId());
					long timestamp = max(count.getLatestMsgTime() + 1,
							clock.currentTimeMillis());
					MessageId parentId = parentItem != null ?
							parentItem.getId() : null;
					createMessage(body, timestamp, parentId, author, handler);
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
			final ResultExceptionHandler<ForumItem, DbException> handler) {
		cryptoExecutor.execute(new Runnable() {
			@Override
			public void run() {
				LOG.info("Creating forum post...");
				ForumPost msg = forumManager
						.createLocalPost(getGroupId(), body, timestamp,
								parentId, author);
				storePost(msg, body, handler);
			}
		});
	}

	@Override
	protected ForumPostHeader addLocalMessage(ForumPost p)
			throws DbException {
		return forumManager.addLocalPost(p);
	}

	@Override
	protected void deleteNamedGroup(Forum forum) throws DbException {
		forumManager.removeForum(forum);
	}

	@Override
	protected ForumItem buildItem(ForumPostHeader header, String body) {
		return new ForumItem(header, body);
	}

}
