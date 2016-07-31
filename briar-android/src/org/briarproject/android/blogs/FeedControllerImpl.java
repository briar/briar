package org.briarproject.android.blogs;

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
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.IdentityManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public class FeedControllerImpl extends DbControllerImpl
		implements FeedController, EventListener {

	private static final Logger LOG =
			Logger.getLogger(FeedControllerImpl.class.getName());

	@Inject
	protected volatile BlogManager blogManager;
	@Inject
	protected volatile IdentityManager identityManager;
	@Inject
	protected volatile EventBus eventBus;

	private volatile OnBlogPostAddedListener listener;

	@Inject
	FeedControllerImpl() {
	}

	@Override
	public void onResume() {
		eventBus.addListener(this);
	}

	@Override
	public void onPause() {
		eventBus.removeListener(this);
	}

	@Override
	public void eventOccurred(Event e) {
		if (!(e instanceof BlogPostAddedEvent)) return;

		LOG.info("New blog post added");
		if (listener != null) {
			final BlogPostAddedEvent m = (BlogPostAddedEvent) e;
			final BlogPostHeader header = m.getHeader();
			try {
				final byte[] body = blogManager.getPostBody(header.getId());
				final BlogPostItem post = new BlogPostItem(header, body);
				listener.onBlogPostAdded(post);
			} catch (DbException ex) {
				if (LOG.isLoggable(WARNING))
					LOG.log(WARNING, ex.toString(), ex);
			}
		}
	}

	@Override
	public void loadPosts(
			final ResultHandler<Collection<BlogPostItem>> resultHandler) {

		LOG.info("Loading blog posts...");
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				Collection<BlogPostItem> posts = new ArrayList<>();
				try {
					// load blog posts
					long now = System.currentTimeMillis();
					for (Blog b : blogManager.getBlogs()) {
						Collection<BlogPostHeader> header =
								blogManager.getPostHeaders(b.getId());
						for (BlogPostHeader h : header) {
							byte[] body = blogManager.getPostBody(h.getId());
							posts.add(new BlogPostItem(h, body));
						}
					}
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading posts took " + duration + " ms");
					resultHandler.onResult(posts);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onResult(null);
				}
			}
		});
	}

	@Override
	public void loadPersonalBlog(final ResultHandler<Blog> resultHandler) {
		LOG.info("Loading personal blog...");
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					// load blog posts
					long now = System.currentTimeMillis();
					Author a =
							identityManager.getLocalAuthors().iterator().next();
					Blog b = blogManager.getPersonalBlog(a);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading pers. blog took " + duration + " ms");
					resultHandler.onResult(b);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onResult(null);
				}
			}
		});
	}

	@Override
	public void setOnBlogPostAddedListener(OnBlogPostAddedListener listener) {
		this.listener = listener;
	}
}
