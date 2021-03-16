package org.briarproject.briar.android.blog;

import android.app.Application;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.android.viewmodel.DbViewModel;
import org.briarproject.briar.android.viewmodel.LiveResult;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogCommentHeader;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogPostHeader;
import org.briarproject.briar.util.HtmlUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import androidx.annotation.UiThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;
import static org.briarproject.briar.util.HtmlUtils.ARTICLE;

@NotNullByDefault
abstract class BaseViewModel extends DbViewModel implements EventListener {

	private static final Logger LOG = getLogger(BaseViewModel.class.getName());

	private final EventBus eventBus;
	protected final IdentityManager identityManager;
	protected final AndroidNotificationManager notificationManager;
	protected final BlogManager blogManager;

	protected final MutableLiveData<LiveResult<List<BlogPostItem>>> blogPosts =
			new MutableLiveData<>();

	// UI thread
	@Nullable
	private Boolean postAddedWasLocal = null;

	BaseViewModel(Application application,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			TransactionManager db,
			AndroidExecutor androidExecutor,
			EventBus eventBus,
			IdentityManager identityManager,
			AndroidNotificationManager notificationManager,
			BlogManager blogManager) {
		super(application, dbExecutor, lifecycleManager, db, androidExecutor);
		this.eventBus = eventBus;
		this.identityManager = identityManager;
		this.notificationManager = notificationManager;
		this.blogManager = blogManager;
		eventBus.addListener(this);
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		eventBus.removeListener(this);
	}

	@DatabaseExecutor
	protected List<BlogPostItem> loadBlogPosts(Transaction txn, GroupId groupId)
			throws DbException {
		long start = now();
		List<BlogPostHeader> headers =
				blogManager.getPostHeaders(txn, groupId);
		logDuration(LOG, "Loading headers", start);
		List<BlogPostItem> items = new ArrayList<>(headers.size());
		start = now();
		for (BlogPostHeader h : headers) {
			BlogPostItem item = getItem(txn, h);
			items.add(item);
		}
		logDuration(LOG, "Loading bodies", start);
		return items;
	}

	@DatabaseExecutor
	protected BlogPostItem getItem(Transaction txn, BlogPostHeader h)
			throws DbException {
		String text;
		if (h instanceof BlogCommentHeader) {
			BlogCommentHeader c = (BlogCommentHeader) h;
			BlogCommentItem item = new BlogCommentItem(c);
			text = getPostText(txn, item.getPostHeader().getId());
			item.setText(text);
			return item;
		} else {
			text = getPostText(txn, h.getId());
			return new BlogPostItem(h, text);
		}
	}

	@DatabaseExecutor
	private String getPostText(Transaction txn, MessageId m)
			throws DbException {
		return HtmlUtils.clean(blogManager.getPostText(txn, m), ARTICLE);
	}

	LiveData<LiveResult<BlogPostItem>> loadBlogPost(GroupId g, MessageId m) {
		MutableLiveData<LiveResult<BlogPostItem>> result =
				new MutableLiveData<>();
		runOnDbThread(true, txn -> {
			long start = now();
			BlogPostHeader header = blogManager.getPostHeader(txn, g, m);
			BlogPostItem item = getItem(txn, header);
			logDuration(LOG, "Loading post", start);
			result.postValue(new LiveResult<>(item));
		}, e -> {
			logException(LOG, WARNING, e);
			result.postValue(new LiveResult<>(e));
		});
		return result;
	}

	protected void onBlogPostAdded(BlogPostHeader header, boolean local) {
		runOnDbThread(true, txn -> {
			BlogPostItem item = getItem(txn, header);
			txn.attach(() -> onBlogPostItemAdded(item, local));
		}, e -> logException(LOG, WARNING, e));
	}

	@UiThread
	private void onBlogPostItemAdded(BlogPostItem item, boolean local) {
		List<BlogPostItem> items = addListItem(blogPosts, item);
		if (items != null) {
			Collections.sort(items);
			postAddedWasLocal = local;
			blogPosts.setValue(new LiveResult<>(items));
		}
	}

	void repeatPost(BlogPostItem item, @Nullable String comment) {
		runOnDbThread(() -> {
			try {
				LocalAuthor a = identityManager.getLocalAuthor();
				Blog b = blogManager.getPersonalBlog(a);
				BlogPostHeader h = item.getHeader();
				blogManager.addLocalComment(a, b.getId(), comment, h);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	LiveData<LiveResult<List<BlogPostItem>>> getBlogPosts() {
		return blogPosts;
	}

	@UiThread
	@Nullable
	Boolean getPostAddedWasLocalAndReset() {
		if (postAddedWasLocal == null) return null;
		boolean wasLocal = postAddedWasLocal;
		postAddedWasLocal = null;
		return wasLocal;
	}

}
