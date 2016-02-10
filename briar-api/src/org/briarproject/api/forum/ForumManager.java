package org.briarproject.api.forum;

import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

public interface ForumManager {

	/** Returns the unique ID of the forum client. */
	ClientId getClientId();

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
}
