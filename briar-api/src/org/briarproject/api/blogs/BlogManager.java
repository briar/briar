package org.briarproject.api.blogs;

import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface BlogManager {

	/** Returns the unique ID of the blog client. */
	ClientId getClientId();

	/** Creates a new Blog. */
	Blog addBlog(LocalAuthor localAuthor, String name, String description)
			throws DbException;

	/** Returns true if a blog can be removed. */
	boolean canBeRemoved(GroupId g) throws DbException;

	/** Removes and deletes a blog. */
	void removeBlog(Blog b) throws DbException;

	/** Stores a local blog post. */
	void addLocalPost(BlogPost p) throws DbException;

	/** Stores a local blog post. */
	void addLocalPost(Transaction txn, BlogPost p) throws DbException;

	/** Add a comment to an existing blog post or reblog it. */
	void addLocalComment(LocalAuthor author, GroupId groupId,
			@Nullable String comment, BlogPostHeader wHeader)
			throws DbException;

	/** Returns the blog with the given ID. */
	Blog getBlog(GroupId g) throws DbException;

	/** Returns the blog with the given ID. */
	Blog getBlog(Transaction txn, GroupId g) throws DbException;

	/** Returns all blogs owned by the given localAuthor. */
	Collection<Blog> getBlogs(LocalAuthor localAuthor) throws DbException;

	/** Returns only the personal blog of the given author. */
	Blog getPersonalBlog(Author author);

	/** Returns all blogs to which the user subscribes. */
	Collection<Blog> getBlogs() throws DbException;

	/** Returns the header of the blog post with the given ID. */
	BlogPostHeader getPostHeader(GroupId g, MessageId m) throws DbException;

	/** Returns the body of the blog post with the given ID. */
	byte[] getPostBody(MessageId m) throws DbException;

	/** Returns the headers of all posts in the given blog. */
	Collection<BlogPostHeader> getPostHeaders(GroupId g) throws DbException;

	/** Marks a blog post as read or unread. */
	void setReadFlag(MessageId m, boolean read) throws DbException;

	/** Registers a hook to be called whenever a blog is removed. */
	void registerRemoveBlogHook(RemoveBlogHook hook);

	interface RemoveBlogHook {
		void removingBlog(Transaction txn, Blog b) throws DbException;
	}

}
