package org.briarproject.api.feed;

import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;

import java.io.IOException;
import java.util.List;

public interface FeedManager {

	/** The unique ID of the RSS feed client. */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.feed");

	/** Adds a RSS feed. */
	void addFeed(String url, GroupId g) throws DbException, IOException;

	/** Removes a RSS feed. */
	void removeFeed(String url) throws DbException;

	/** Gets a list of all added RSS feeds */
	List<Feed> getFeeds() throws DbException;

}
