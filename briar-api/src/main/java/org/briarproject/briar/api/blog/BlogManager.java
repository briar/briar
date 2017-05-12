package org.briarproject.briar.api.blog;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import java.util.Collection;

import javax.annotation.Nullable;

@NotNullByDefault
public interface BlogManager {

	/**
	 * Unique ID of the blog client.
	 */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.blog");

	/**
	 * Adds a blog from the given author.
	 */
	Blog addBlog(Author author) throws DbException;

	/**
	 * Adds the given {@link Blog} within the given {@link Transaction}.
	 */
	void addBlog(Transaction txn, Blog b) throws DbException;

	/**
	 * Returns true if a blog can be removed.
	 */
	boolean canBeRemoved(Blog b) throws DbException;

	/**
	 * Removes and deletes a blog.
	 */
	void removeBlog(Blog b) throws DbException;

	/**
	 * Removes and deletes a blog with the given {@link Transaction}.
	 */
	void removeBlog(Transaction txn, Blog b) throws DbException;

	/**
	 * Stores a local blog post.
	 */
	void addLocalPost(BlogPost p) throws DbException;

	/**
	 * Stores a local blog post.
	 */
	void addLocalPost(Transaction txn, BlogPost p) throws DbException;

	/**
	 * Adds a comment to an existing blog post or reblogs it.
	 */
	void addLocalComment(LocalAuthor author, GroupId groupId,
			@Nullable String comment, BlogPostHeader parentHeader)
			throws DbException;

	/**
	 * Returns the blog with the given ID.
	 */
	Blog getBlog(GroupId g) throws DbException;

	/**
	 * Returns the blog with the given ID.
	 */
	Blog getBlog(Transaction txn, GroupId g) throws DbException;

	/**
	 * Returns all blogs owned by the given localAuthor.
	 */
	Collection<Blog> getBlogs(LocalAuthor localAuthor) throws DbException;

	/**
	 * Returns only the personal blog of the given author.
	 */
	Blog getPersonalBlog(Author author);

	/**
	 * Returns all blogs to which the user subscribes.
	 */
	Collection<Blog> getBlogs() throws DbException;

	/**
	 * Returns the header of the blog post with the given ID.
	 */
	BlogPostHeader getPostHeader(GroupId g, MessageId m) throws DbException;

	/**
	 * Returns the body of the blog post with the given ID.
	 */
	String getPostBody(MessageId m) throws DbException;

	/**
	 * Returns the headers of all posts in the given blog.
	 */
	Collection<BlogPostHeader> getPostHeaders(GroupId g) throws DbException;

	/**
	 * Marks a blog post as read or unread.
	 */
	void setReadFlag(MessageId m, boolean read) throws DbException;

	/**
	 * Registers a hook to be called whenever a blog is removed.
	 */
	void registerRemoveBlogHook(RemoveBlogHook hook);

	interface RemoveBlogHook {
		void removingBlog(Transaction txn, Blog b) throws DbException;
	}

}
