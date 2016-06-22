package org.briarproject.android.forum;

import android.app.Activity;

import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.api.FormatException;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.event.MessageStateChangedEvent;
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
import java.util.Stack;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.sync.ValidationManager.State.DELIVERED;

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
	protected volatile ForumPostFactory forumPostFactory;
	@Inject
	protected volatile CryptoComponent crypto;
	@Inject
	protected volatile ForumManager forumManager;
	@Inject
	protected volatile EventBus eventBus;
	@Inject
	protected volatile IdentityManager identityManager;
	@Inject
	protected ForumPersistentData data;

	private ForumPostListener listener;
	private MessageId localAdd = null;

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
		if (activity.isFinishing()) {
			data.clearAll();
		}
	}

	private void findSingleNewEntry() {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				List<ForumEntry> oldEntries = getForumEntries();
				data.clearHeaders();
				try {
					loadPosts();
					List<ForumEntry> allEntries = getForumEntries();
					int i = 0;
					for (ForumEntry entry : allEntries) {
						boolean isNew = true;
						for (ForumEntry oldEntry : oldEntries) {
							if (entry.getMessageId()
									.equals(oldEntry.getMessageId())) {
								isNew = false;
								break;
							}
						}
						if (isNew) {
							if (localAdd != null &&
									entry.getMessageId().equals(localAdd)) {
								addLocalEntry(i, entry);
							} else {
								addForeignEntry(i, entry);
							}
							break;
						}
						i++;
					}
				} catch (DbException e) {
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof MessageStateChangedEvent) {
			MessageStateChangedEvent m = (MessageStateChangedEvent) e;
			if (m.getState() == DELIVERED &&
					m.getMessage().getGroupId().equals(data.getGroupId())) {
				LOG.info("Message added, reloading");
				findSingleNewEntry();
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent s = (GroupRemovedEvent) e;
			if (s.getGroup().getId().equals(data.getGroupId())) {
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

	private void loadAuthor() throws DbException {
		Collection<LocalAuthor> localAuthors =
				identityManager.getLocalAuthors();

		for (LocalAuthor author : localAuthors) {
			if (author == null)
				continue;
			data.setLocalAuthor(author);
			break;
		}
	}

	private void loadPosts() throws DbException {
		long now = System.currentTimeMillis();
		Collection<ForumPostHeader> headers =
				forumManager.getPostHeaders(data.getGroupId());
		data.addHeaders(headers);
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Loading headers took " + duration + " ms");
		now = System.currentTimeMillis();
		for (ForumPostHeader header : headers) {
			if (data.getBody(header.getId()) == null) {
				byte[] body = forumManager.getPostBody(header.getId());
				data.addBody(header.getId(), body);
			}
		}
		duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Loading bodies took " + duration + " ms");
	}

	@Override
	public void loadForum(final GroupId groupId,
			final UiResultHandler<Boolean> resultHandler) {
		LOG.info("Loading forum...");

		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					if (data.getGroupId() == null ||
							!data.getGroupId().equals(groupId)) {
						data.clearAll();
						data.setGroupId(groupId);
						long now = System.currentTimeMillis();
						data.setForum(forumManager.getForum(groupId));
						long duration = System.currentTimeMillis() - now;
						if (LOG.isLoggable(INFO))
							LOG.info("Loading forum took " + duration +
									" ms");
						now = System.currentTimeMillis();
						loadAuthor();
						duration = System.currentTimeMillis() - now;
						if (LOG.isLoggable(INFO))
							LOG.info("Loading author took " + duration +
									" ms");
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
	public String getForumName() {
		return data.getForum() == null ? null : data.getForum().getName();
	}

	@Override
	public List<ForumEntry> getForumEntries() {
		if (data.getForumEntries() != null) {
			return data.getForumEntries();
		}
		Collection<ForumPostHeader> headers = data.getHeaders();
		List<ForumEntry> forumEntries = new ArrayList<>();
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
			forumEntries.add(new ForumEntry(h,
					StringUtils.fromUtf8(data.getBody(h.getId())),
					idStack.size()));
		}
		data.setForumEntries(forumEntries);
		return forumEntries;
	}

	@Override
	public void unsubscribe(final UiResultHandler<Boolean> resultHandler) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					forumManager.removeForum(data.getForum());
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
				Collection<ForumPostHeader> headers = data.getHeaders();
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
					byte[] b = data.getLocalAuthor().getPrivateKey();
					PrivateKey authorKey = keyParser.parsePrivateKey(b);
					p = forumPostFactory.createPseudonymousPost(
							data.getGroupId(), timestamp, parentId,
							data.getLocalAuthor(), "text/plain", body,
							authorKey);
				} catch (GeneralSecurityException | FormatException e) {
					throw new RuntimeException(e);
				}
				storePost(p);
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
					localAdd = p.getMessage().getId();
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

}
