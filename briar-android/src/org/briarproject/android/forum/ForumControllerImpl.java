package org.briarproject.android.forum;

import android.app.Activity;
import android.support.annotation.Nullable;

import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.android.controller.handler.ResultHandler;
import org.briarproject.api.FormatException;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.ForumPostReceivedEvent;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.identity.Author.Status.OURSELVES;

public class ForumControllerImpl extends DbControllerImpl
		implements ForumController, EventListener {

	private static final Logger LOG =
			Logger.getLogger(ForumControllerImpl.class.getName());

	@Inject
	protected Activity activity;
	@Inject
	@CryptoExecutor
	protected Executor cryptoExecutor;
	@Inject
	volatile ForumPostFactory forumPostFactory;
	@Inject
	protected volatile CryptoComponent crypto;
	@Inject
	protected volatile ForumManager forumManager;
	@Inject
	protected volatile EventBus eventBus;
	@Inject
	protected volatile IdentityManager identityManager;

	private final Map<MessageId, byte[]> bodyCache = new ConcurrentHashMap<>();
	private final AtomicLong newestTimeStamp = new AtomicLong();

	private volatile LocalAuthor localAuthor = null;
	private volatile Forum forum = null;

	private ForumPostListener listener;

	@Inject
	ForumControllerImpl() {

	}

	@Override
	public void onActivityCreate() {
		if (activity instanceof ForumPostListener) {
			listener = (ForumPostListener) activity;
		} else {
			throw new IllegalStateException(
					"An activity that injects the ForumController must " +
							"implement the ForumPostListener");
		}
	}

	@Override
	public void onActivityResume() {
		eventBus.addListener(this);
	}

	@Override
	public void onActivityPause() {
		eventBus.removeListener(this);
	}

	@Override
	public void onActivityDestroy() {
	}

	@Override
	public void eventOccurred(Event e) {
		if (forum == null) return;
		if (e instanceof ForumPostReceivedEvent) {
			final ForumPostReceivedEvent pe = (ForumPostReceivedEvent) e;
			if (pe.getGroupId().equals(forum.getId())) {
				LOG.info("Forum post received, adding...");
				final ForumPostHeader fph = pe.getForumPostHeader();
				updateNewestTimestamp(fph.getTimestamp());
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						listener.onExternalEntryAdded(fph);
					}
				});
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent s = (GroupRemovedEvent) e;
			if (s.getGroup().getId().equals(forum.getId())) {
				LOG.info("Forum removed");
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						activity.finish();
					}
				});
			}
		}
	}

	/**
	 * This should only be run from the DbThread.
	 *
	 * @throws DbException
	 */
	private void loadForum(GroupId groupId) throws DbException {
		// Get Forum
		long now = System.currentTimeMillis();
		forum = forumManager.getForum(groupId);
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Loading forum took " + duration + " ms");

		// Get First Identity
		now = System.currentTimeMillis();
		localAuthor = identityManager.getLocalAuthor();
		duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Loading author took " + duration + " ms");
	}

	/**
	 * This should only be run from the DbThread.
	 *
	 * @throws DbException
	 */
	private Collection<ForumPostHeader> loadHeaders() throws DbException {
		if (forum == null)
			throw new RuntimeException("Forum has not been initialized");

		// Get Headers
		long now = System.currentTimeMillis();
		Collection<ForumPostHeader> headers =
				forumManager.getPostHeaders(forum.getId());
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Loading headers took " + duration + " ms");
		return headers;
	}

	/**
	 * This should only be run from the DbThread.
	 *
	 * @throws DbException
	 */
	private void loadBodies(Collection<ForumPostHeader> headers)
			throws DbException {
		// Get Bodies
		long now = System.currentTimeMillis();
		for (ForumPostHeader header : headers) {
			if (!bodyCache.containsKey(header.getId())) {
				byte[] body = forumManager.getPostBody(header.getId());
				bodyCache.put(header.getId(), body);
			}
		}
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Loading bodies took " + duration + " ms");
	}

	private List<ForumEntry> buildForumEntries(
			Collection<ForumPostHeader> headers) {
		List<ForumEntry> entries = new ArrayList<>();
		for (ForumPostHeader h : headers) {
			byte[] body = bodyCache.get(h.getId());
			entries.add(new ForumEntry(h, StringUtils.fromUtf8(body)));
		}
		return entries;
	}

	private void updateNewestTimeStamp(Collection<ForumPostHeader> headers) {
		for (ForumPostHeader h : headers) {
			updateNewestTimestamp(h.getTimestamp());
		}
	}

	@Override
	public void loadForum(final GroupId groupId,
			final ResultExceptionHandler<List<ForumEntry>, DbException> resultHandler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				LOG.info("Loading forum...");
				try {
					if (forum == null) {
						loadForum(groupId);
					}
					// Get Forum Posts and Bodies
					Collection<ForumPostHeader> headers = loadHeaders();
					updateNewestTimeStamp(headers);
					loadBodies(headers);
					resultHandler.onResult(buildForumEntries(headers));
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onException(e);
				}
			}
		});
	}

	@Override
	@Nullable
	public Forum getForum() {
		return forum;
	}

	@Override
	public void loadPost(final ForumPostHeader header,
			final ResultExceptionHandler<ForumEntry, DbException> resultHandler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				LOG.info("Loading post...");
				try {
					loadBodies(Collections.singletonList(header));
					resultHandler.onResult(new ForumEntry(header, StringUtils
							.fromUtf8(bodyCache.get(header.getId()))));
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onException(e);
				}
			}
		});
	}

	@Override
	public void unsubscribe(final ResultHandler<Boolean> resultHandler) {
		if (forum == null) return;
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					forumManager.removeForum(forum);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Removing forum took " + duration + " ms");
					resultHandler.onResult(true);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onResult(false);
				}
			}
		});
	}

	@Override
	public void entryRead(ForumEntry forumEntry) {
		entriesRead(Collections.singletonList(forumEntry));
	}

	@Override
	public void entriesRead(final Collection<ForumEntry> forumEntries) {
		if (forum == null) return;
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					for (ForumEntry fe : forumEntries) {
						forumManager
								.setReadFlag(forum.getId(), fe.getId(), true);
					}
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Marking read took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	@Override
	public void createPost(byte[] body,
			ResultExceptionHandler<ForumEntry, DbException> resultHandler) {
		createPost(body, null, resultHandler);
	}

	@Override
	public void createPost(final byte[] body, final MessageId parentId,
			final ResultExceptionHandler<ForumEntry, DbException> resultHandler) {
		cryptoExecutor.execute(new Runnable() {
			@Override
			public void run() {
				LOG.info("Create post...");
				long timestamp = System.currentTimeMillis();
				timestamp = Math.max(timestamp, newestTimeStamp.get());
				ForumPost p;
				try {
					KeyParser keyParser = crypto.getSignatureKeyParser();
					byte[] b = localAuthor.getPrivateKey();
					PrivateKey authorKey = keyParser.parsePrivateKey(b);
					p = forumPostFactory.createPseudonymousPost(
							forum.getId(), timestamp, parentId, localAuthor,
							"text/plain", body, authorKey);
				} catch (GeneralSecurityException | FormatException e) {
					throw new RuntimeException(e);
				}
				bodyCache.put(p.getMessage().getId(), body);
				storePost(p, resultHandler);
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

					resultHandler.onResult(new ForumEntry(h, StringUtils
							.fromUtf8(bodyCache.get(p.getMessage().getId()))));

				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onException(e);
				}
			}
		});
	}

	private void updateNewestTimestamp(long update) {
		long newest = newestTimeStamp.get();
		while (newest < update) {
			if (newestTimeStamp.compareAndSet(newest, update)) return;
			newest = newestTimeStamp.get();
		}
	}
}
