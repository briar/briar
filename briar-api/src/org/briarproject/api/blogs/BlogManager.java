package org.briarproject.api.blogs;

import org.briarproject.api.FormatException;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
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

	/** Stores a local blog post. */
	void addLocalPost(BlogPost p) throws DbException;

	/** Returns the blog with the given ID. */
	Blog getBlog(GroupId g) throws DbException;

	/** Returns the blog with the given ID. */
	Blog getBlog(Transaction txn, GroupId g) throws DbException;

	/** Returns all blogs to which the localAuthor created. */
	Collection<Blog> getBlogs(LocalAuthor localAuthor) throws DbException;

	/** Returns all blogs to which the user subscribes. */
	Collection<Blog> getBlogs() throws DbException;

	/** Returns the body of the blog post with the given ID. */
	@Nullable
	byte[] getPostBody(MessageId m) throws DbException;

	/** Returns the headers of all posts in the given blog. */
	Collection<BlogPostHeader> getPostHeaders(GroupId g) throws DbException;

	/** Marks a blog post as read or unread. */
	void setReadFlag(MessageId m, boolean read) throws DbException;

}
