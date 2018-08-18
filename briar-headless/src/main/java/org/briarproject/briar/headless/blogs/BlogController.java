package org.briarproject.briar.headless.blogs;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogPost;
import org.briarproject.briar.api.blog.BlogPostFactory;
import org.briarproject.briar.api.blog.BlogPostHeader;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import javax.inject.Singleton;

import io.javalin.Context;

@Immutable
@Singleton
@MethodsNotNullByDefault
@ParametersAreNonnullByDefault
public class BlogController {

	private final BlogManager blogManager;
	private final BlogPostFactory blogPostFactory;
	private final IdentityManager identityManager;
	private final Clock clock;

	@Inject
	public BlogController(BlogManager blogManager,
			BlogPostFactory blogPostFactory, IdentityManager identityManager,
			Clock clock) {
		this.blogManager = blogManager;
		this.blogPostFactory = blogPostFactory;
		this.identityManager = identityManager;
		this.clock = clock;
	}

	public Context listPosts(Context ctx) throws DbException {
		List<OutputBlogPost> posts = new ArrayList<>();
		for (Blog b : blogManager.getBlogs()) {
			Collection<BlogPostHeader> headers =
					blogManager.getPostHeaders(b.getId());
			for (BlogPostHeader header : headers) {
				String body = blogManager.getPostBody(header.getId());
				OutputBlogPost post = new OutputBlogPost(header, body);
				posts.add(post);
			}
		}
		return ctx.json(posts);
	}

	public Context createPost(Context ctx)
			throws DbException, GeneralSecurityException, FormatException {
		String text = ctx.formParam("text");
		if (text == null || text.length() < 1) {
			return ctx.status(500).result("Expecting Blog text");
		} else {
			LocalAuthor author = identityManager.getLocalAuthor();
			Blog blog = blogManager.getPersonalBlog(author);
			long now = clock.currentTimeMillis();
			BlogPost post = blogPostFactory
					.createBlogPost(blog.getId(), now, null, author, text);
			blogManager.addLocalPost(post);
			BlogPostHeader header = blogManager
					.getPostHeader(blog.getId(), post.getMessage().getId());
			OutputBlogPost outputPost = new OutputBlogPost(header, text);
			return ctx.json(outputPost);
		}
	}

}
