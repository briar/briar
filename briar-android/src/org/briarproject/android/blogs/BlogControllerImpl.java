package org.briarproject.android.blogs;

import android.app.Activity;

import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.BlogPostAddedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public class BlogControllerImpl extends DbControllerImpl
		implements BlogController, EventListener {

	private static final Logger LOG =
			Logger.getLogger(BlogControllerImpl.class.getName());

	@Inject
	protected Activity activity;
	@Inject
	protected EventBus eventBus;
	@Inject
	protected AndroidNotificationManager notificationManager;

	@Inject
	protected volatile BlogManager blogManager;

	private final Map<MessageId, byte[]> bodyCache = new ConcurrentHashMap<>();

	private volatile BlogPostListener listener;
	private volatile GroupId groupId = null;

	@Inject
	BlogControllerImpl() {
	}

	@Override
	public void setGroupId(GroupId g) {
		groupId = g;
	}

	@Override
	public void onActivityCreate() {
		if (activity instanceof BlogPostListener) {
			listener = (BlogPostListener) activity;
		} else {
			throw new IllegalStateException(
					"An activity that injects the BlogController must " +
							"implement the BlogPostListener");
		}
	}

	@Override
	public void onActivityResume() {
		notificationManager.blockNotification(groupId);
		notificationManager.clearBlogPostNotification(groupId);
		eventBus.addListener(this);
	}

	@Override
	public void onActivityPause() {
		notificationManager.unblockNotification(groupId);
		eventBus.removeListener(this);
	}

	@Override
	public void onActivityDestroy() {
	}

	@Override
	public void eventOccurred(Event e) {
		if (groupId == null) throw new IllegalStateException();
		if (e instanceof BlogPostAddedEvent) {
			final BlogPostAddedEvent m = (BlogPostAddedEvent) e;
			if (m.getGroupId().equals(groupId)) {
				LOG.info("New blog post added");
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						listener.onBlogPostAdded(m.getHeader(), m.isLocal());
					}
				});
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent s = (GroupRemovedEvent) e;
			if (s.getGroup().getId().equals(groupId)) {
				LOG.info("Blog removed");
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						// TODO: Not the controller's job, add a listener method
						activity.finish();
					}
				});
			}
		}
	}

	@Override
	public void loadBlogPosts(
			final ResultExceptionHandler<Collection<BlogPostItem>, DbException> handler) {
		if (groupId == null) throw new IllegalStateException();
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Collection<BlogPostHeader> headers =
							blogManager.getPostHeaders(groupId);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading headers took " + duration + " ms");
					List<BlogPostItem> items = new ArrayList<>(headers.size());
					now = System.currentTimeMillis();
					for (BlogPostHeader h : headers) {
						byte[] body = getPostBody(h.getId());
						items.add(new BlogPostItem(groupId, h, body));
					}
					duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading bodies took " + duration + " ms");
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
	public void loadBlogPost(final BlogPostHeader header,
			final ResultExceptionHandler<BlogPostItem, DbException> handler) {
		if (groupId == null) throw new IllegalStateException();
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					byte[] body = getPostBody(header.getId());
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading body took " + duration + " ms");
					handler.onResult(new BlogPostItem(groupId, header, body));
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	@Override
	public void loadBlogPost(final MessageId m,
			final ResultExceptionHandler<BlogPostItem, DbException> handler) {
		if (groupId == null) throw new IllegalStateException();
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					BlogPostHeader header = blogManager.getPostHeader(m);
					byte[] body = getPostBody(m);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading post took " + duration + " ms");
					handler.onResult(new BlogPostItem(groupId, header, body));
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	private byte[] getPostBody(MessageId m) throws DbException {
		byte[] body = bodyCache.get(m);
		if (body == null) {
			body = blogManager.getPostBody(m);
			if (body != null) bodyCache.put(m, body);
		}
		return body;
	}

	@Override
	public void canDeleteBlog(
			final ResultExceptionHandler<Boolean, DbException> handler) {
		if (groupId == null) throw new IllegalStateException();
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					handler.onResult(blogManager.canBeRemoved(groupId));
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	@Override
	public void deleteBlog(
			final ResultExceptionHandler<Void, DbException> handler) {
		if (groupId == null) throw new IllegalStateException();
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					Blog b = blogManager.getBlog(groupId);
					blogManager.removeBlog(b);
					handler.onResult(null);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

}
