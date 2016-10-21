package org.briarproject.android.blogs;

import android.app.Activity;

import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.controller.ActivityLifecycleController;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.BlogPostAddedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public class BlogControllerImpl extends BaseControllerImpl
		implements ActivityLifecycleController, BlogController, EventListener {

	private static final Logger LOG =
			Logger.getLogger(BlogControllerImpl.class.getName());

	private volatile GroupId groupId = null;

	@Inject
	BlogControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, EventBus eventBus,
			AndroidNotificationManager notificationManager,
			IdentityManager identityManager, BlogManager blogManager) {
		super(dbExecutor, lifecycleManager, eventBus, notificationManager,
				identityManager, blogManager);
	}

	@Override
	public void onActivityCreate(Activity activity) {
	}

	@Override
	public void onActivityStart() {
		super.onStart();
		notificationManager.blockNotification(groupId);
		notificationManager.clearBlogPostNotification(groupId);
	}

	@Override
	public void onActivityStop() {
		super.onStop();
		notificationManager.unblockNotification(groupId);
	}

	@Override
	public void onActivityDestroy() {
	}

	@Override
	public void setGroupId(GroupId g) {
		groupId = g;
	}

	@Override
	public void eventOccurred(Event e) {
		if (groupId == null) throw new IllegalStateException();
		if (e instanceof BlogPostAddedEvent) {
			BlogPostAddedEvent b = (BlogPostAddedEvent) e;
			if (b.getGroupId().equals(groupId)) {
				LOG.info("Blog post added");
				onBlogPostAdded(b.getHeader(), b.isLocal());
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent g = (GroupRemovedEvent) e;
			if (g.getGroup().getId().equals(groupId)) {
				LOG.info("Blog removed");
				onBlogRemoved();
			}
		}
	}

	@Override
	public void loadBlogPosts(
			final ResultExceptionHandler<Collection<BlogPostItem>, DbException> handler) {
		if (groupId == null) throw new IllegalStateException();
		loadBlogPosts(groupId, handler);
	}

	@Override
	public void loadBlogPost(final MessageId m,
			final ResultExceptionHandler<BlogPostItem, DbException> handler) {
		if (groupId == null) throw new IllegalStateException();
		loadBlogPost(groupId, m, handler);
	}

	@Override
	public void loadBlog(
			final ResultExceptionHandler<BlogItem, DbException> handler) {
		if (groupId == null) throw new IllegalStateException();
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					LocalAuthor a = identityManager.getLocalAuthor();
					Blog b = blogManager.getBlog(groupId);
					boolean ours = a.getId().equals(b.getAuthor().getId());
					boolean removable = blogManager.canBeRemoved(groupId);
					BlogItem blog = new BlogItem(b, ours, removable);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading blog took " + duration + " ms");
					handler.onResult(blog);
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
					long now = System.currentTimeMillis();
					Blog b = blogManager.getBlog(groupId);
					blogManager.removeBlog(b);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Removing blog took " + duration + " ms");
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
