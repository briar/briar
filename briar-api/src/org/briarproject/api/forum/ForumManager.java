package org.briarproject.api.forum;

import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface ForumManager extends MessageTracker {

	/** Returns the unique ID of the forum client. */
	ClientId getClientId();

	/** Subscribes to a forum. */
	Forum addForum(String name) throws DbException;

	/** Unsubscribes from a forum. */
	void removeForum(Forum f) throws DbException;

	/** Creates a local forum post. */
	ForumPost createLocalPost(GroupId groupId, String text,
			@Nullable MessageId parentId) throws DbException;

	/** Stores a local forum post. */
	ForumPostHeader addLocalPost(ForumPost p) throws DbException;

	/** Returns the forum with the given ID. */
	Forum getForum(GroupId g) throws DbException;

	/** Returns the forum with the given ID. */
	Forum getForum(Transaction txn, GroupId g) throws DbException;

	/** Returns all forums to which the user subscribes. */
	Collection<Forum> getForums() throws DbException;

	/** Returns the body of the forum post with the given ID. */
	byte[] getPostBody(MessageId m) throws DbException;

	/** Returns the headers of all posts in the given forum. */
	Collection<ForumPostHeader> getPostHeaders(GroupId g) throws DbException;

	/** Registers a hook to be called whenever a forum is removed. */
	void registerRemoveForumHook(RemoveForumHook hook);

	interface RemoveForumHook {
		void removingForum(Transaction txn, Forum f) throws DbException;
	}
}
