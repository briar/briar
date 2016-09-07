package org.briarproject.android.blogs;

import android.app.Activity;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;

import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogCommentHeader;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.BlogPostAddedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

abstract class BaseControllerImpl extends DbControllerImpl
		implements BaseController, EventListener {

	private static final Logger LOG =
			Logger.getLogger(BaseControllerImpl.class.getName());

	@Inject
	protected Activity activity;
	@Inject
	protected EventBus eventBus;
	@Inject
	protected AndroidNotificationManager notificationManager;
	@Inject
	protected IdentityManager identityManager;

	@Inject
	protected volatile BlogManager blogManager;

	private final Map<MessageId, String> bodyCache = new ConcurrentHashMap<>();
	private final Map<MessageId, BlogPostHeader> headerCache =
			new ConcurrentHashMap<>();

	protected volatile OnBlogPostAddedListener listener;

	@Override
	@CallSuper
	public void onStart() {
		eventBus.addListener(this);
	}

	@Override
	@CallSuper
	public void onStop() {
		eventBus.removeListener(this);
	}

	@Override
	@CallSuper
	public void eventOccurred(Event e) {
		if (e instanceof BlogPostAddedEvent) {
			final BlogPostAddedEvent m = (BlogPostAddedEvent) e;
			LOG.info("New blog post added");
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					listener.onBlogPostAdded(m.getHeader(), m.isLocal());
				}
			});
		}
	}

	@Override
	public void setOnBlogPostAddedListener(OnBlogPostAddedListener listener) {
		this.listener = listener;
	}

	@Override
	public void loadBlogPosts(final GroupId groupId,
			final ResultExceptionHandler<Collection<BlogPostItem>, DbException> handler) {
		if (groupId == null) throw new IllegalStateException();
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					Collection<BlogPostItem> items = loadItems(groupId);
					handler.onResult(items);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	protected Collection<BlogPostItem> loadItems(GroupId groupId)
			throws DbException {
		long now = System.currentTimeMillis();
		Collection<BlogPostHeader> headers =
				blogManager.getPostHeaders(groupId);
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Loading headers took " + duration + " ms");
		Collection<BlogPostItem> items = new ArrayList<>(headers.size());
		now = System.currentTimeMillis();
		for (BlogPostHeader h : headers) {
			headerCache.put(h.getId(), h);
			BlogPostItem item = getItem(h);
			items.add(item);
		}
		duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Loading bodies took " + duration + " ms");
		return items;
	}

	@Override
	public void loadBlogPost(final BlogPostHeader header,
			final ResultExceptionHandler<BlogPostItem, DbException> handler) {

		String body = bodyCache.get(header.getId());
		if (body != null) {
			LOG.info("Loaded body from cache");
			handler.onResult(new BlogPostItem(header, body));
			return;
		}
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					BlogPostItem item = getItem(header);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading body took " + duration + " ms");
					handler.onResult(item);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	@Override
	public void loadBlogPost(final GroupId g, final MessageId m,
			final ResultExceptionHandler<BlogPostItem, DbException> handler) {

		BlogPostHeader header = headerCache.get(m);
		if (header != null) {
			LOG.info("Loaded header from cache");
			loadBlogPost(header, handler);
			return;
		}
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					BlogPostHeader header = getPostHeader(g, m);
					BlogPostItem item = getItem(header);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading post took " + duration + " ms");
					handler.onResult(item);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	@Override
	public void repeatPost(final BlogPostItem item,
			final @Nullable String comment,
			final ResultExceptionHandler<Void, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					LocalAuthor a = identityManager.getLocalAuthor();
					Blog b = blogManager.getPersonalBlog(a);
					BlogPostHeader h = item.getHeader();
					blogManager.addLocalComment(a, b.getId(), comment, h);
					handler.onResult(null);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	private BlogPostHeader getPostHeader(GroupId g, MessageId m)
			throws DbException {

		if (g == null) throw new IllegalStateException();
		BlogPostHeader header = headerCache.get(m);
		if (header == null) {
			header = blogManager.getPostHeader(g, m);
			headerCache.put(m, header);
		}
		return header;
	}

	private BlogPostItem getItem(BlogPostHeader h) throws DbException {
		String body;
		if (h instanceof BlogCommentHeader) {
			BlogCommentHeader c = (BlogCommentHeader) h;
			BlogCommentItem item = new BlogCommentItem(c);
			body = getPostBody(item.getPostHeader().getId());
			item.setBody(body);
			return item;
		} else {
			body = getPostBody(h.getId());
			return new BlogPostItem(h, body);
		}
	}

	private String getPostBody(MessageId m) throws DbException {
		String body = bodyCache.get(m);
		if (body == null) {
			body = blogManager.getPostBody(m);
			if (body != null) bodyCache.put(m, body);
		}
		//noinspection ConstantConditions
		return body;
	}

}
