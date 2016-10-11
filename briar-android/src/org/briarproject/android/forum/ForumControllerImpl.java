package org.briarproject.android.forum;

import android.support.annotation.Nullable;

import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.android.threaded.ThreadListControllerImpl;
import org.briarproject.api.FormatException;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.ForumPostReceivedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.identity.Author.Status.OURSELVES;

public class ForumControllerImpl
		extends ThreadListControllerImpl<Forum, ForumEntry, ForumPostHeader>
		implements ForumController {

	private static final Logger LOG =
			Logger.getLogger(ForumControllerImpl.class.getName());

	private final ForumPostFactory forumPostFactory;
	private final ForumManager forumManager;

	@Inject
	ForumControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			@CryptoExecutor Executor cryptoExecutor,
			ForumPostFactory forumPostFactory, CryptoComponent crypto,
			ForumManager forumManager, EventBus eventBus,
			IdentityManager identityManager,
			AndroidNotificationManager notificationManager) {
		super(dbExecutor, lifecycleManager, cryptoExecutor, crypto, eventBus,
				identityManager, notificationManager);
		this.forumManager = forumManager;
		this.forumPostFactory = forumPostFactory;
	}

	@Override
	public void onActivityResume() {
		super.onActivityResume();
		notificationManager.clearForumPostNotification(groupId);
	}

	@Override
	public void eventOccurred(Event e) {
		super.eventOccurred(e);

		if (e instanceof ForumPostReceivedEvent) {
			final ForumPostReceivedEvent pe = (ForumPostReceivedEvent) e;
			if (pe.getGroupId().equals(groupId)) {
				LOG.info("Forum post received, adding...");
				final ForumPostHeader fph = pe.getForumPostHeader();
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
	protected Forum loadGroupItem() throws DbException {
		return forumManager.getForum(groupId);
	}

	@Override
	protected Collection<ForumPostHeader> loadHeaders() throws DbException {
		return forumManager.getPostHeaders(groupId);
	}

	@Override
	protected void loadBodies(Collection<ForumPostHeader> headers)
			throws DbException {
		for (ForumPostHeader header : headers) {
			if (!bodyCache.containsKey(header.getId())) {
				String body = StringUtils
						.fromUtf8(forumManager.getPostBody(header.getId()));
				bodyCache.put(header.getId(), body);
			}
		}
	}

	@Override
	protected void markRead(MessageId id) throws DbException {
		forumManager.setReadFlag(groupId, id, true);
	}

	@Override
	public void send(final String body, @Nullable final MessageId parentId,
			final ResultExceptionHandler<ForumEntry, DbException> handler) {
		cryptoExecutor.execute(new Runnable() {
			@Override
			public void run() {
				LOG.info("Create post...");
				long timestamp = System.currentTimeMillis();
				timestamp = Math.max(timestamp, newestTimeStamp.get());
				ForumPost p;
				try {
					LocalAuthor a = identityManager.getLocalAuthor();
					KeyParser keyParser = crypto.getSignatureKeyParser();
					byte[] k = a.getPrivateKey();
					PrivateKey authorKey = keyParser.parsePrivateKey(k);
					byte[] b = StringUtils.toUtf8(body);
					p = forumPostFactory
							.createPseudonymousPost(groupId, timestamp,
									parentId, a, "text/plain", b, authorKey);
				} catch (GeneralSecurityException | FormatException e) {
					throw new RuntimeException(e);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
					return;
				}
				bodyCache.put(p.getMessage().getId(), body);
				storePost(p, handler);
			}
		});
	}

	private void storePost(final ForumPost p,
			final ResultExceptionHandler<ForumEntry, DbException> resultHandler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					LOG.info("Store post...");
					long now = System.currentTimeMillis();
					forumManager.addLocalPost(p);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Storing message took " + duration + " ms");

					ForumPostHeader h =
							new ForumPostHeader(p.getMessage().getId(),
									p.getParent(),
									p.getMessage().getTimestamp(),
									p.getAuthor(), OURSELVES, true);

					resultHandler.onResult(buildItem(h));
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onException(e);
				}
			}
		});
	}

	@Override
	protected void deleteGroupItem(Forum forum) throws DbException {
		forumManager.removeForum(forum);
	}

	@Override
	protected ForumEntry buildItem(ForumPostHeader header) {
		return new ForumEntry(header, bodyCache.get(header.getId()));
	}

}
