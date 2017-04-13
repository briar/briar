package org.briarproject.briar.api.feed;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MINUTES;

public interface FeedConstants {

	/* delay after start before fetching feed */
	int FETCH_DELAY_INITIAL = 1;

	/* the interval the feed should be fetched */
	int FETCH_INTERVAL = 30;

	/* the unit that applies to the fetch times */
	TimeUnit FETCH_UNIT = MINUTES;

	// group metadata keys
	String KEY_FEEDS = "feeds";
	String KEY_FEED_URL = "feedURL";
	String KEY_BLOG_TITLE = "blogTitle";
	String KEY_PUBLIC_KEY = "publicKey";
	String KEY_PRIVATE_KEY = "privateKey";
	String KEY_FEED_DESC = "feedDesc";
	String KEY_FEED_AUTHOR = "feedAuthor";
	String KEY_FEED_ADDED = "feedAdded";
	String KEY_FEED_UPDATED = "feedUpdated";
	String KEY_FEED_LAST_ENTRY = "feedLastEntryTime";

}
