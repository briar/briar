package org.briarproject.api.forum;

import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

public interface ForumManager {

	/** Returns the unique ID of the forum client. */
	ClientId getClientId();

	/** Creates a forum with the given name. */
	Forum createForum(String name);

	/** Creates a forum with the given name and salt. */
	Forum createForum(String name, byte[] salt);

	/** Subscribes to a forum. */
	void addForum(Forum f) throws DbException;

	/** Unsubscribes from a forum. */
	void removeForum(Forum f) throws DbException;

	/** Stores a local forum post. */
	void addLocalPost(ForumPost p) throws DbException;

	/** Returns the forum with the given ID. */
	Forum getForum(GroupId g) throws DbException;

	/** Returns all forums to which the user subscribes. */
	Collection<Forum> getForums() throws DbException;

	/** Returns the body of the forum post with the given ID. */
	byte[] getPostBody(MessageId m) throws DbException;

	/** Returns the headers of all posts in the given forum. */
	Collection<ForumPostHeader> getPostHeaders(GroupId g) throws DbException;

	/** Marks a forum post as read or unread. */
	void setReadFlag(MessageId m, boolean read) throws DbException;

	/** Registers a hook to be called whenever a forum is removed. */
	void registerRemoveForumHook(RemoveForumHook hook);

	interface RemoveForumHook {
		void removingForum(Transaction txn, Forum f) throws DbException;
	}
}
