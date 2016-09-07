package org.briarproject.android.blogs;

import org.briarproject.android.controller.ActivityLifecycleController;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.BlogPostAddedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;

public class BlogControllerImpl extends BaseControllerImpl
		implements ActivityLifecycleController, BlogController, EventListener {

	private static final Logger LOG =
			Logger.getLogger(BlogControllerImpl.class.getName());

	private volatile GroupId groupId = null;

	@Inject
	BlogControllerImpl() {
	}

	@Override
	public void onActivityCreate() {
	}

	@Override
	public void onActivityResume() {
		super.onStart(); // TODO: Should be called when activity starts. #609
		notificationManager.blockNotification(groupId);
		notificationManager.clearBlogPostNotification(groupId);
	}

	@Override
	public void onActivityPause() {
		super.onStop(); // TODO: Should be called when activity stops. #609
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
			BlogPostAddedEvent s = (BlogPostAddedEvent) e;
			if (s.getGroupId().equals(groupId)) {
				super.eventOccurred(e);
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
					LocalAuthor a = identityManager.getLocalAuthor();
					Blog b = blogManager.getBlog(groupId);
					boolean ours = a.getId().equals(b.getAuthor().getId());
					boolean removable = blogManager.canBeRemoved(groupId);
					BlogItem blog = new BlogItem(b,
							Collections.<BlogPostHeader>emptyList(),
							ours, removable);
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
