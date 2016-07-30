package org.briarproject.android.blogs;

import android.app.Activity;
import android.support.annotation.Nullable;

import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.ResultHandler;
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
import java.util.SortedSet;
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

	private volatile BlogPostListener listener;
	private volatile GroupId groupId = null;
	// FIXME: This collection isn't thread-safe, isn't updated atomically
	private volatile TreeSet<BlogPostItem> posts = null;

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
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof BlogPostAddedEvent) {
			final BlogPostAddedEvent m = (BlogPostAddedEvent) e;
			if (m.getGroupId().equals(groupId)) {
				LOG.info("New blog post added");
				if (posts == null) {
					LOG.info("Posts have not loaded, yet");
					// FIXME: Race condition, new post may not get loaded
					return;
				}
				final BlogPostHeader header = m.getHeader();
				// FIXME: Don't make blocking calls in event handlers
				try {
					final byte[] body = blogManager.getPostBody(header.getId());
					final BlogPostItem post = new BlogPostItem(header, body);
					posts.add(post);
					listener.onBlogPostAdded(post, m.isLocal());
				} catch (DbException ex) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, ex.toString(), ex);
				}
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent s = (GroupRemovedEvent) e;
			if (s.getGroup().getId().equals(groupId)) {
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
	public void loadBlog(final GroupId g, final boolean reload,
			final ResultHandler<Boolean> resultHandler) {

		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					if (reload || posts == null) {
						groupId = g;
						posts = new TreeSet<>();
						// load blog posts
						long now = System.currentTimeMillis();
						Collection<BlogPostItem> newPosts = new ArrayList<>();
						Collection<BlogPostHeader> header =
								blogManager.getPostHeaders(g);
						for (BlogPostHeader h : header) {
							byte[] body = blogManager.getPostBody(h.getId());
							newPosts.add(new BlogPostItem(h, body));
						}
						posts.addAll(newPosts);
						long duration = System.currentTimeMillis() - now;
						if (LOG.isLoggable(INFO))
							LOG.info("Loading blog took " + duration + " ms");
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
	public SortedSet<BlogPostItem> getBlogPosts() {
		return posts;
	}

	@Override
	@Nullable
	public BlogPostItem getBlogPost(MessageId id) {
		if (posts == null) return null;
		for (BlogPostItem item : posts) {
			if (item.getId().equals(id)) return item;
		}
		return null;
	}

	@Override
	@Nullable
	public MessageId getBlogPostId(int position) {
		if (posts == null) return null;
		int i = 0;
		for (BlogPostItem post : posts) {
			if (i == position) return post.getId();
			i++;
		}
		return null;
	}

	@Override
	public void deleteBlog(final ResultHandler<Boolean> resultHandler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				if (groupId == null) {
					resultHandler.onResult(false);
					return;
				}
				try {
					Blog b = blogManager.getBlog(groupId);
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
