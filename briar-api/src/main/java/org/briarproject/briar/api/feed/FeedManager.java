package org.briarproject.briar.api.feed;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;

import java.io.IOException;
import java.util.List;

@NotNullByDefault
public interface FeedManager {

	/**
	 * The unique ID of the RSS feed client.
	 */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.feed");

	/**
	 * Adds an RSS feed as a new dedicated blog.
	 */
	void addFeed(String url) throws DbException, IOException;

	/**
	 * Removes an RSS feed.
	 */
	void removeFeed(Feed feed) throws DbException;

	/**
	 * Returns a list of all added RSS feeds
	 */
	List<Feed> getFeeds() throws DbException;

}
