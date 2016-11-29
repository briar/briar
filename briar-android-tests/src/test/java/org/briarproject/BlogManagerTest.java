package org.briarproject;

import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogCommentHeader;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogPost;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.db.DbException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Collection;
import java.util.Iterator;

import static junit.framework.Assert.assertFalse;
import static org.briarproject.api.blogs.MessageType.COMMENT;
import static org.briarproject.api.blogs.MessageType.POST;
import static org.briarproject.api.blogs.MessageType.WRAPPED_COMMENT;
import static org.briarproject.api.blogs.MessageType.WRAPPED_POST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlogManagerTest extends BriarIntegrationTest {

	private BlogManager blogManager0, blogManager1;
	private Blog blog0, blog1;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		author0 = identityManager0.getLocalAuthor();
		author1 = identityManager1.getLocalAuthor();

		blogManager0 = c0.getBlogManager();
		blogManager1 = c1.getBlogManager();

		blog0 = blogFactory.createBlog(author0);
		blog1 = blogFactory.createBlog(author1);
	}

	@Test
	public void testPersonalBlogInitialisation() throws Exception {
		Collection<Blog> blogs0 = blogManager0.getBlogs();
		assertEquals(3, blogs0.size());
		Iterator<Blog> i0 = blogs0.iterator();
		assertEquals(author0, i0.next().getAuthor());
		assertEquals(author1, i0.next().getAuthor());
		assertEquals(author2, i0.next().getAuthor());

		Collection<Blog> blogs1 = blogManager1.getBlogs();
		assertEquals(2, blogs1.size());
		Iterator<Blog> i1 = blogs1.iterator();
		assertEquals(author1, i1.next().getAuthor());
		assertEquals(author0, i1.next().getAuthor());

		assertEquals(blog0, blogManager0.getPersonalBlog(author0));
		assertEquals(blog0, blogManager1.getPersonalBlog(author0));
		assertEquals(blog1, blogManager0.getPersonalBlog(author1));
		assertEquals(blog1, blogManager1.getPersonalBlog(author1));

		assertEquals(blog0, blogManager0.getBlog(blog0.getId()));
		assertEquals(blog0, blogManager1.getBlog(blog0.getId()));
		assertEquals(blog1, blogManager0.getBlog(blog1.getId()));
		assertEquals(blog1, blogManager1.getBlog(blog1.getId()));

		assertEquals(1, blogManager0.getBlogs(author0).size());
		assertEquals(1, blogManager1.getBlogs(author0).size());
		assertEquals(1, blogManager0.getBlogs(author1).size());
		assertEquals(1, blogManager1.getBlogs(author1).size());
	}

	@Test
	public void testBlogPost() throws Exception {
		// check that blog0 has no posts
		final String body = TestUtils.getRandomString(42);
		Collection<BlogPostHeader> headers0 =
				blogManager0.getPostHeaders(blog0.getId());
		assertEquals(0, headers0.size());

		// add a post to blog0
		BlogPost p = blogPostFactory
				.createBlogPost(blog0.getId(), clock.currentTimeMillis(), null,
						author0, body);
		blogManager0.addLocalPost(p);

		// check that post is now in blog0
		headers0 = blogManager0.getPostHeaders(blog0.getId());
		assertEquals(1, headers0.size());

		// check that body is there
		assertEquals(body, blogManager0.getPostBody(p.getMessage().getId()));

		// make sure that blog0 at author1 doesn't have the post yet
		Collection<BlogPostHeader> headers1 =
				blogManager1.getPostHeaders(blog0.getId());
		assertEquals(0, headers1.size());

		// sync the post over
		sync0To1(1, true);

		// make sure post arrived
		headers1 = blogManager1.getPostHeaders(blog0.getId());
		assertEquals(1, headers1.size());
		assertEquals(POST, headers1.iterator().next().getType());

		// check that body is there
		assertEquals(body, blogManager1.getPostBody(p.getMessage().getId()));
	}

	@Test
	public void testBlogPostInWrongBlog() throws Exception {
		// add a post to blog1
		final String body = TestUtils.getRandomString(42);
		BlogPost p = blogPostFactory
				.createBlogPost(blog1.getId(), clock.currentTimeMillis(), null,
						author0, body);
		blogManager0.addLocalPost(p);

		// check that post is now in blog1
		Collection<BlogPostHeader> headers0 =
				blogManager0.getPostHeaders(blog1.getId());
		assertEquals(1, headers0.size());

		// sync the post over
		sync0To1(1, false);

		// make sure post did not arrive, because of wrong signature
		Collection<BlogPostHeader> headers1 =
				blogManager1.getPostHeaders(blog1.getId());
		assertEquals(0, headers1.size());
	}

	@Test
	public void testCanNotRemoveContactsPersonalBlog() throws Exception {
		assertFalse(blogManager0.canBeRemoved(blog1.getId()));
		assertFalse(blogManager1.canBeRemoved(blog0.getId()));

		// the following two calls should throw a DbException now
		thrown.expect(DbException.class);

		blogManager0.removeBlog(blog1);
		blogManager1.removeBlog(blog0);

		// blogs have not been removed
		assertEquals(2, blogManager0.getBlogs().size());
		assertEquals(2, blogManager1.getBlogs().size());
	}

	@Test
	public void testBlogComment() throws Exception {
		// add a post to blog0
		final String body = TestUtils.getRandomString(42);
		BlogPost p = blogPostFactory
				.createBlogPost(blog0.getId(), clock.currentTimeMillis(), null,
						author0, body);
		blogManager0.addLocalPost(p);

		// sync the post over
		sync0To1(1, true);

		// make sure post arrived
		Collection<BlogPostHeader> headers1 =
				blogManager1.getPostHeaders(blog0.getId());
		assertEquals(1, headers1.size());
		assertEquals(POST, headers1.iterator().next().getType());

		// 1 adds a comment to that blog post
		String comment = "This is a comment on a blog post!";
		blogManager1
				.addLocalComment(author1, blog1.getId(), comment,
						headers1.iterator().next());

		// sync comment over
		sync1To0(2, true);

		// make sure comment and wrapped post arrived
		Collection<BlogPostHeader> headers0 =
				blogManager0.getPostHeaders(blog1.getId());
		assertEquals(1, headers0.size());
		assertEquals(COMMENT, headers0.iterator().next().getType());
		BlogCommentHeader h = (BlogCommentHeader) headers0.iterator().next();
		assertEquals(author0, h.getParent().getAuthor());

		// ensure that body can be retrieved from wrapped post
		assertEquals(body, blogManager0.getPostBody(h.getParentId()));

		// 1 has only their own comment in their blog
		headers1 = blogManager1.getPostHeaders(blog1.getId());
		assertEquals(1, headers1.size());
	}

	@Test
	public void testBlogCommentOnOwnPost() throws Exception {
		// add a post to blog0
		final String body = TestUtils.getRandomString(42);
		BlogPost p = blogPostFactory
				.createBlogPost(blog0.getId(), clock.currentTimeMillis(), null,
						author0, body);
		blogManager0.addLocalPost(p);

		// get header of own post
		Collection<BlogPostHeader> headers0 =
				blogManager0.getPostHeaders(blog0.getId());
		assertEquals(1, headers0.size());
		BlogPostHeader header = headers0.iterator().next();

		// add a comment on own post
		String comment = "This is a comment on my own blog post!";
		blogManager0
				.addLocalComment(author0, blog0.getId(), comment, header);

		// sync the post and comment over
		sync0To1(2, true);

		// make sure post arrived
		Collection<BlogPostHeader> headers1 =
				blogManager1.getPostHeaders(blog0.getId());
		assertEquals(2, headers1.size());
		for (BlogPostHeader h : headers1) {
			if (h.getType() == POST) {
				assertEquals(body, blogManager1.getPostBody(h.getId()));
			} else {
				assertEquals(comment, ((BlogCommentHeader)h).getComment());
			}
		}
	}

	@Test
	public void testCommentOnComment() throws Exception {
		// add a post to blog0
		final String body = TestUtils.getRandomString(42);
		BlogPost p = blogPostFactory
				.createBlogPost(blog0.getId(), clock.currentTimeMillis(), null,
						author0, body);
		blogManager0.addLocalPost(p);

		// sync the post over
		sync0To1(1, true);

		// make sure post arrived
		Collection<BlogPostHeader> headers1 =
				blogManager1.getPostHeaders(blog0.getId());
		assertEquals(1, headers1.size());
		assertEquals(POST, headers1.iterator().next().getType());

		// 1 reblogs that blog post
		blogManager1
				.addLocalComment(author1, blog1.getId(), null,
						headers1.iterator().next());

		// sync comment over
		sync1To0(2, true);

		// make sure comment and wrapped post arrived
		Collection<BlogPostHeader> headers0 =
				blogManager0.getPostHeaders(blog1.getId());
		assertEquals(1, headers0.size());

		// get header of comment
		BlogPostHeader cHeader = headers0.iterator().next();
		assertEquals(COMMENT, cHeader.getType());

		// comment on the comment
		String comment = "This is a comment on a reblogged post.";
		blogManager0
				.addLocalComment(author0, blog0.getId(), comment, cHeader);

		// sync comment over
		sync0To1(3, true);

		// check that comment arrived
		headers1 =
				blogManager1.getPostHeaders(blog0.getId());
		assertEquals(2, headers1.size());

		// get header of comment
		cHeader = null;
		for (BlogPostHeader h : headers1) {
			if (h.getType() == COMMENT) {
				cHeader = h;
			}
		}
		assertTrue(cHeader != null);

		// another comment on the comment
		String comment2 = "This is a comment on a comment.";
		blogManager1.addLocalComment(author1, blog1.getId(), comment2, cHeader);

		// sync comment over
		sync1To0(4, true);

		// make sure new comment arrived
		headers0 =
				blogManager0.getPostHeaders(blog1.getId());
		assertEquals(2, headers0.size());
		boolean satisfied = false;
		for (BlogPostHeader h : headers0) {
			assertEquals(COMMENT, h.getType());
			BlogCommentHeader c = (BlogCommentHeader) h;
			if (c.getComment() != null && c.getComment().equals(comment2)) {
				assertEquals(author0, c.getParent().getAuthor());
				assertEquals(WRAPPED_COMMENT, c.getParent().getType());
				assertEquals(comment,
						((BlogCommentHeader) c.getParent()).getComment());
				assertEquals(WRAPPED_COMMENT,
						((BlogCommentHeader) c.getParent()).getParent()
								.getType());
				assertEquals(WRAPPED_POST,
						((BlogCommentHeader) ((BlogCommentHeader) c
								.getParent()).getParent()).getParent()
								.getType());
				satisfied = true;
			}
		}
		assertTrue(satisfied);
	}

	@Test
	public void testCommentOnOwnComment() throws Exception {
		// add a post to blog0
		final String body = TestUtils.getRandomString(42);
		BlogPost p = blogPostFactory
				.createBlogPost(blog0.getId(), clock.currentTimeMillis(), null,
						author0, body);
		blogManager0.addLocalPost(p);

		// sync the post over
		sync0To1(1, true);

		// make sure post arrived
		Collection<BlogPostHeader> headers1 =
				blogManager1.getPostHeaders(blog0.getId());
		assertEquals(1, headers1.size());
		assertEquals(POST, headers1.iterator().next().getType());

		// 1 reblogs that blog post with a comment
		String comment = "This is a comment on a post.";
		blogManager1
				.addLocalComment(author1, blog1.getId(), comment,
						headers1.iterator().next());

		// get comment from own blog
		headers1 = blogManager1.getPostHeaders(blog1.getId());
		assertEquals(1, headers1.size());
		assertEquals(COMMENT, headers1.iterator().next().getType());
		BlogCommentHeader ch = (BlogCommentHeader) headers1.iterator().next();
		assertEquals(comment, ch.getComment());

		comment = "This is a comment on a post with a comment.";
		blogManager1.addLocalComment(author1, blog1.getId(), comment, ch);

		// sync both comments over (2 comments + 1 wrapped post)
		sync1To0(3, true);

		// make sure both comments arrived
		Collection<BlogPostHeader> headers0 =
				blogManager0.getPostHeaders(blog1.getId());
		assertEquals(2, headers0.size());
	}

}
