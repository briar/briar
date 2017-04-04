package org.briarproject.briar.android.blog;

import android.support.annotation.CallSuper;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.android.controller.DbControllerImpl;
import org.briarproject.briar.android.controller.handler.ExceptionHandler;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogCommentHeader;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogPostHeader;
import org.briarproject.briar.util.HtmlUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.briar.util.HtmlUtils.ARTICLE;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
abstract class BaseControllerImpl extends DbControllerImpl
		implements BaseController, EventListener {

	private static final Logger LOG =
			Logger.getLogger(BaseControllerImpl.class.getName());

	protected final EventBus eventBus;
	protected final AndroidNotificationManager notificationManager;
	protected final IdentityManager identityManager;
	protected final BlogManager blogManager;

	private final Map<MessageId, String> bodyCache = new ConcurrentHashMap<>();
	private final Map<MessageId, BlogPostHeader> headerCache =
			new ConcurrentHashMap<>();

	private volatile BlogListener listener;

	BaseControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, EventBus eventBus,
			AndroidNotificationManager notificationManager,
			IdentityManager identityManager, BlogManager blogManager) {
		super(dbExecutor, lifecycleManager);
		this.eventBus = eventBus;
		this.notificationManager = notificationManager;
		this.identityManager = identityManager;
		this.blogManager = blogManager;
	}

	@Override
	@CallSuper
	public void onStart() {
		if (listener == null) throw new IllegalStateException();
		eventBus.addListener(this);
	}

	@Override
	@CallSuper
	public void onStop() {
		eventBus.removeListener(this);
	}

	@Override
	public void setBlogListener(BlogListener listener) {
		this.listener = listener;
	}

	void onBlogPostAdded(final BlogPostHeader h, final boolean local) {
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				listener.onBlogPostAdded(h, local);
			}
		});
	}

	void onBlogRemoved() {
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				listener.onBlogRemoved();
			}
		});
	}


	@Override
	public void loadBlogPosts(final GroupId groupId,
			final ResultExceptionHandler<Collection<BlogPostItem>, DbException> handler) {
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

	Collection<BlogPostItem> loadItems(GroupId groupId) throws DbException {
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
			final ExceptionHandler<DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					LocalAuthor a = identityManager.getLocalAuthor();
					Blog b = blogManager.getPersonalBlog(a);
					BlogPostHeader h = item.getHeader();
					blogManager.addLocalComment(a, b.getId(), comment, h);
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
		BlogPostHeader header = headerCache.get(m);
		if (header == null) {
			header = blogManager.getPostHeader(g, m);
			headerCache.put(m, header);
		}
		return header;
	}

	@DatabaseExecutor
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

	@DatabaseExecutor
	private String getPostBody(MessageId m) throws DbException {
		String body = bodyCache.get(m);
		if (body == null) {
			body = HtmlUtils.clean(blogManager.getPostBody(m), ARTICLE);
			bodyCache.put(m, body);
		}
		//noinspection ConstantConditions
		return body;
	}

}
