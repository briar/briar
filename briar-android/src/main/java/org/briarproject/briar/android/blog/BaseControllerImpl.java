package org.briarproject.briar.android.blog;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import androidx.annotation.CallSuper;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;
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

	private final Map<MessageId, String> textCache = new ConcurrentHashMap<>();
	private final Map<MessageId, BlogPostHeader> headerCache =
			new ConcurrentHashMap<>();

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
		eventBus.addListener(this);
	}

	@Override
	@CallSuper
	public void onStop() {
		eventBus.removeListener(this);
	}

	@Override
	public void loadBlogPosts(GroupId groupId,
			ResultExceptionHandler<List<BlogPostItem>, DbException> handler) {
		runOnDbThread(() -> {
			try {
				List<BlogPostItem> items = loadItems(groupId);
				handler.onResult(items);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
			}
		});
	}

	List<BlogPostItem> loadItems(GroupId groupId) throws DbException {
		long start = now();
		Collection<BlogPostHeader> headers =
				blogManager.getPostHeaders(groupId);
		logDuration(LOG, "Loading headers", start);
		List<BlogPostItem> items = new ArrayList<>(headers.size());
		start = now();
		for (BlogPostHeader h : headers) {
			headerCache.put(h.getId(), h);
			BlogPostItem item = getItem(h);
			items.add(item);
		}
		logDuration(LOG, "Loading bodies", start);
		return items;
	}

	@Override
	public void loadBlogPost(BlogPostHeader header,
			ResultExceptionHandler<BlogPostItem, DbException> handler) {

		String text = textCache.get(header.getId());
		if (text != null) {
			LOG.info("Loaded text from cache");
			handler.onResult(new BlogPostItem(header, text));
			return;
		}
		runOnDbThread(() -> {
			try {
				long start = now();
				BlogPostItem item = getItem(header);
				logDuration(LOG, "Loading text", start);
				handler.onResult(item);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
			}
		});
	}

	@Override
	public void loadBlogPost(GroupId g, MessageId m,
			ResultExceptionHandler<BlogPostItem, DbException> handler) {

		BlogPostHeader header = headerCache.get(m);
		if (header != null) {
			LOG.info("Loaded header from cache");
			loadBlogPost(header, handler);
			return;
		}
		runOnDbThread(() -> {
			try {
				long start = now();
				BlogPostHeader header1 = getPostHeader(g, m);
				BlogPostItem item = getItem(header1);
				logDuration(LOG, "Loading post", start);
				handler.onResult(item);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
			}
		});
	}

	@Override
	public void repeatPost(BlogPostItem item, @Nullable String comment,
			ExceptionHandler<DbException> handler) {
		runOnDbThread(() -> {
			try {
				LocalAuthor a = identityManager.getLocalAuthor();
				Blog b = blogManager.getPersonalBlog(a);
				BlogPostHeader h = item.getHeader();
				blogManager.addLocalComment(a, b.getId(), comment, h);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
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
		String text;
		if (h instanceof BlogCommentHeader) {
			BlogCommentHeader c = (BlogCommentHeader) h;
			BlogCommentItem item = new BlogCommentItem(c);
			text = getPostText(item.getPostHeader().getId());
			item.setText(text);
			return item;
		} else {
			text = getPostText(h.getId());
			return new BlogPostItem(h, text);
		}
	}

	@DatabaseExecutor
	private String getPostText(MessageId m) throws DbException {
		String text = textCache.get(m);
		if (text == null) {
			text = HtmlUtils.clean(blogManager.getPostText(m), ARTICLE);
			textCache.put(m, text);
		}
		return text;
	}

}
