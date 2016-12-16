package org.briarproject.briar.api.forum;

import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;

import java.util.Collection;

import javax.annotation.Nullable;

@NotNullByDefault
public interface ForumManager {

	/**
	 * The unique ID of the forum client.
	 */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.forum");

	/**
	 * Subscribes to a forum.
	 */
	Forum addForum(String name) throws DbException;

	/**
	 * Subscribes to a forum within the given {@link Transaction}.
	 */
	void addForum(Transaction txn, Forum f) throws DbException;

	/**
	 * Unsubscribes from a forum.
	 */
	void removeForum(Forum f) throws DbException;

	/**
	 * Creates a local forum post.
	 */
	@CryptoExecutor
	ForumPost createLocalPost(GroupId groupId, String body, long timestamp,
			@Nullable MessageId parentId, LocalAuthor author);

	/**
	 * Stores a local forum post.
	 */
	ForumPostHeader addLocalPost(ForumPost p) throws DbException;

	/**
	 * Returns the forum with the given ID.
	 */
	Forum getForum(GroupId g) throws DbException;

	/**
	 * Returns the forum with the given ID.
	 */
	Forum getForum(Transaction txn, GroupId g) throws DbException;

	/**
	 * Returns all forums to which the user subscribes.
	 */
	Collection<Forum> getForums() throws DbException;

	/**
	 * Returns the body of the forum post with the given ID.
	 */
	String getPostBody(MessageId m) throws DbException;

	/**
	 * Returns the headers of all posts in the given forum.
	 */
	Collection<ForumPostHeader> getPostHeaders(GroupId g) throws DbException;

	/**
	 * Registers a hook to be called whenever a forum is removed.
	 */
	void registerRemoveForumHook(RemoveForumHook hook);

	/**
	 * Returns the group count for the given forum.
	 */
	GroupCount getGroupCount(GroupId g) throws DbException;

	/**
	 * Marks a message as read or unread and updates the group count.
	 */
	void setReadFlag(GroupId g, MessageId m, boolean read) throws DbException;

	interface RemoveForumHook {
		void removingForum(Transaction txn, Forum f) throws DbException;
	}
}
