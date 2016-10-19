package org.briarproject.android.forum;

import android.support.annotation.Nullable;

import org.briarproject.android.api.AndroidNotificationManager;
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
import org.briarproject.util.StringUtils;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

public class ForumControllerImpl
		extends ThreadListControllerImpl<Forum, ForumItem, ForumPostHeader, ForumPost>
		implements ForumController {

	private static final Logger LOG =
			Logger.getLogger(ForumControllerImpl.class.getName());

	private final ForumManager forumManager;

	@Inject
	ForumControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, IdentityManager identityManager,
			@CryptoExecutor Executor cryptoExecutor,
			ForumManager forumManager, EventBus eventBus,
			AndroidNotificationManager notificationManager, Clock clock) {
		super(dbExecutor, lifecycleManager, identityManager, cryptoExecutor,
				eventBus, notificationManager, clock);
		this.forumManager = forumManager;
	}

	@Override
	public void onActivityResume() {
		super.onActivityResume();
		notificationManager.clearForumPostNotification(getGroupId());
	}

	@Override
	public void eventOccurred(Event e) {
		super.eventOccurred(e);

		if (e instanceof ForumPostReceivedEvent) {
			final ForumPostReceivedEvent pe = (ForumPostReceivedEvent) e;
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
	protected String loadMessageBody(MessageId id) throws DbException {
		return StringUtils.fromUtf8(forumManager.getPostBody(id));
	}

	@Override
	protected void markRead(MessageId id) throws DbException {
		forumManager.setReadFlag(getGroupId(), id, true);
	}

	@Override
	protected long getLatestTimestamp() throws DbException {
		GroupCount count = forumManager.getGroupCount(getGroupId());
		return count.getLatestMsgTime();
	}

	@Override
	protected ForumPost createLocalMessage(String body, long timestamp,
			@Nullable MessageId parentId, LocalAuthor author) {
		return forumManager
				.createLocalPost(getGroupId(), body, timestamp, parentId,
						author);
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
