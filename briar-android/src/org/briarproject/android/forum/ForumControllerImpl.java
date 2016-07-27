package org.briarproject.android.forum;

import android.app.Activity;
import android.support.annotation.Nullable;

import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.ResultHandler;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.api.FormatException;
import org.briarproject.api.clients.MessageTree;
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
import org.briarproject.clients.MessageTreeImpl;
import org.briarproject.util.StringUtils;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.identity.Author.Status.VERIFIED;

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

	private volatile MessageTree<ForumPostHeader> tree =
			new MessageTreeImpl<>();
	private volatile Map<MessageId, byte[]> bodyCache = new HashMap<>();
	private volatile LocalAuthor localAuthor = null;
	private volatile Forum forum = null;
	private volatile List<ForumEntry> forumEntries = null;

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
			ForumPostReceivedEvent pe = (ForumPostReceivedEvent) e;
			if (pe.getGroupId().equals(forum.getId())) {
				LOG.info("Forum Post received, adding...");
				addNewPost(pe.getForumPostHeader());
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

	private void addNewPost(final ForumPostHeader h) {
		if (forum == null) return;
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				if (!bodyCache.containsKey(h.getId())) {
					try {
						byte[] body = forumManager.getPostBody(h.getId());
						bodyCache.put(h.getId(), body);
					} catch (DbException e) {
						if (LOG.isLoggable(WARNING))
							LOG.log(WARNING, e.toString(), e);
						return;
					}
				}

				tree.add(h);
				forumEntries = null;
				// FIXME we should not need to calculate the index here
				//       the index is essentially stored in two different locations
				int i = 0;
				for (ForumEntry entry : getForumEntries()) {
					if (entry.getMessageId().equals(h.getId())) {
						if (localAuthor != null && localAuthor.equals(h.getAuthor())) {
							addLocalEntry(i, entry);
						} else {
							addForeignEntry(i, entry);
						}
					}
					i++;
				}
			}
		});
	}

	/**
	 * This should only be run from the DbThread.
	 * @throws DbException
	 */
	private void loadPosts() throws DbException {
		if (forum == null)
			throw new RuntimeException("Forum has not been initialized");

		// Get Headers
		long now = System.currentTimeMillis();
		Collection<ForumPostHeader> headers =
				forumManager.getPostHeaders(forum.getId());
		tree.add(headers);
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Loading headers took " + duration + " ms");

		// Get Bodies
		now = System.currentTimeMillis();
		for (ForumPostHeader header : headers) {
			if (!bodyCache.containsKey(header.getId())) {
				byte[] body = forumManager.getPostBody(header.getId());
				bodyCache.put(header.getId(), body);
			}
		}
		duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Loading bodies took " + duration + " ms");
	}

	@Override
	public void loadForum(final GroupId groupId,
			final ResultHandler<Boolean> resultHandler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				LOG.info("Loading forum...");
				try {
					if (forum == null) {
						// Get Forum
						long now = System.currentTimeMillis();
						forum = forumManager.getForum(groupId);
						long duration = System.currentTimeMillis() - now;
						if (LOG.isLoggable(INFO))
							LOG.info("Loading forum took " + duration +
									" ms");

						// Get First Identity
						now = System.currentTimeMillis();
						localAuthor =
								identityManager.getLocalAuthors().iterator()
										.next();
						duration = System.currentTimeMillis() - now;
						if (LOG.isLoggable(INFO))
							LOG.info("Loading author took " + duration +
									" ms");

						// Get Forum Posts and Bodies
						loadPosts();
					}
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
	@Nullable
	public Forum getForum() {
		return forum;
	}

	@Override
	public List<ForumEntry> getForumEntries() {
		if (forumEntries != null) {
			return forumEntries;
		}
		Collection<ForumPostHeader> headers = getHeaders();
		List<ForumEntry> entries = new ArrayList<>();
		Stack<MessageId> idStack = new Stack<>();

		for (ForumPostHeader h : headers) {
			if (h.getParentId() == null) {
				idStack.clear();
			} else if (idStack.isEmpty() ||
					!idStack.contains(h.getParentId())) {
				idStack.push(h.getParentId());
			} else if (!h.getParentId().equals(idStack.peek())) {
				do {
					idStack.pop();
				} while (!h.getParentId().equals(idStack.peek()));
			}
			byte[] body = bodyCache.get(h.getId());
			entries.add(new ForumEntry(h, StringUtils.fromUtf8(body),
						idStack.size()));
		}
		forumEntries = entries;
		return entries;
	}

	@Override
	public void unsubscribe(final UiResultHandler<Boolean> resultHandler) {
		if (forum == null) return;
		runOnDbThread(new Runnable() {
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
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					for (ForumEntry fe : forumEntries) {
						forumManager.setReadFlag(fe.getMessageId(), true);
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
	public void createPost(byte[] body) {
		createPost(body, null);
	}

	@Override
	public void createPost(final byte[] body, final MessageId parentId) {
		cryptoExecutor.execute(new Runnable() {
			public void run() {
				long timestamp = System.currentTimeMillis();
				long newestTimeStamp = 0;
				Collection<ForumPostHeader> headers = getHeaders();
				if (headers != null) {
					for (ForumPostHeader h : headers) {
						if (h.getTimestamp() > newestTimeStamp)
							newestTimeStamp = h.getTimestamp();
					}
				}
				// Don't use an earlier timestamp than the newest post
				if (timestamp < newestTimeStamp) {
					timestamp = newestTimeStamp;
				}
				ForumPost p;
				try {
					KeyParser keyParser = crypto.getSignatureKeyParser();
					byte[] b = localAuthor.getPrivateKey();
					PrivateKey authorKey = keyParser.parsePrivateKey(b);
					p = forumPostFactory.createPseudonymousPost(
							forum.getId(), timestamp, parentId,
							localAuthor, "text/plain", body,
							authorKey);
				} catch (GeneralSecurityException | FormatException e) {
					throw new RuntimeException(e);
				}
				bodyCache.put(p.getMessage().getId(), body);
				storePost(p);
				addNewPost(p);
			}
		});
	}

	private void addLocalEntry(final int index, final ForumEntry entry) {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				listener.addLocalEntry(index, entry);
			}
		});
	}

	private void addForeignEntry(final int index, final ForumEntry entry) {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				listener.addForeignEntry(index, entry);
			}
		});
	}

	private void storePost(final ForumPost p) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					forumManager.addLocalPost(p);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info(
								"Storing message took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void addNewPost(final ForumPost p) {
		ForumPostHeader h =
				new ForumPostHeader(p.getMessage().getId(), p.getParent(),
						p.getMessage().getTimestamp(), p.getAuthor(), VERIFIED,
						p.getContentType(), false);
		addNewPost(h);
	}

	private Collection<ForumPostHeader> getHeaders() {
		return tree.depthFirstOrder();
	}

}
