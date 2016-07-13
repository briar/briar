package org.briarproject.android.blogs;

import android.app.Activity;
import android.support.annotation.Nullable;

import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.UiResultHandler;
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
import java.util.TreeSet;
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
	protected volatile BlogManager blogManager;
	@Inject
	protected volatile EventBus eventBus;
	@Inject
	protected BlogPersistentData data;

	private volatile BlogPostListener listener;

	@Inject
	BlogControllerImpl() {
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

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof BlogPostAddedEvent) {
			final BlogPostAddedEvent m = (BlogPostAddedEvent) e;
			if (m.getGroupId().equals(data.getGroupId())) {
				LOG.info("New blog post added");
				final BlogPostHeader header = m.getHeader();
				try {
					final byte[] body = blogManager.getPostBody(header.getId());
					final BlogPostItem post = new BlogPostItem(header, body);
					data.addPost(post);
					listener.onBlogPostAdded(post, m.isLocal());
				} catch (DbException ex) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, ex.toString(), ex);
				}
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent s = (GroupRemovedEvent) e;
			if (s.getGroup().getId().equals(data.getGroupId())) {
				LOG.info("Blog removed");
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						activity.finish();
					}
				});
			}
		}
	}

	@Override
	public void loadBlog(final GroupId groupId, final boolean reload,
			final UiResultHandler<Boolean> resultHandler) {

		LOG.info("Loading blog...");
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					if (reload || data.getGroupId() == null ||
							!data.getGroupId().equals(groupId)) {
						data.setGroupId(groupId);
						// load blog posts
						long now = System.currentTimeMillis();
						Collection<BlogPostItem> posts = new ArrayList<>();
						Collection<BlogPostHeader> header =
								blogManager.getPostHeaders(groupId);
						for (BlogPostHeader h : header) {
							byte[] body = blogManager.getPostBody(h.getId());
							posts.add(new BlogPostItem(h, body));
						}
						data.setPosts(posts);
						long duration = System.currentTimeMillis() - now;
						if (LOG.isLoggable(INFO))
							LOG.info("Post header load took " + duration +
									" ms");
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
	public TreeSet<BlogPostItem> getBlogPosts() {
		return data.getBlogPosts();
	}

	@Override
	@Nullable
	public BlogPostItem getBlogPost(MessageId id) {
		for (BlogPostItem item : getBlogPosts()) {
			if (item.getId().equals(id)) return item;
		}
		return null;
	}

	@Override
	@Nullable
	public MessageId getBlogPostId(int position) {
		int i = 0;
		for (BlogPostItem post : getBlogPosts()) {
			if (i == position) return post.getId();
			i++;
		}
		return null;
	}

	@Override
	public void deleteBlog(final UiResultHandler<Boolean> resultHandler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				if (data.getGroupId() == null) {
					resultHandler.onResult(false);
					return;
				}
				try {
					Blog b = blogManager.getBlog(data.getGroupId());
					blogManager.removeBlog(b);
					resultHandler.onResult(true);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onResult(false);
				}
			}
		});
	}

}
