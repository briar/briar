package org.briarproject.api.feed;

import org.briarproject.api.FormatException;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.sync.GroupId;
import org.jetbrains.annotations.Nullable;

import static org.briarproject.api.feed.FeedConstants.KEY_BLOG_GROUP_ID;
import static org.briarproject.api.feed.FeedConstants.KEY_FEED_ADDED;
import static org.briarproject.api.feed.FeedConstants.KEY_FEED_AUTHOR;
import static org.briarproject.api.feed.FeedConstants.KEY_FEED_DESC;
import static org.briarproject.api.feed.FeedConstants.KEY_FEED_LAST_ENTRY;
import static org.briarproject.api.feed.FeedConstants.KEY_FEED_TITLE;
import static org.briarproject.api.feed.FeedConstants.KEY_FEED_UPDATED;
import static org.briarproject.api.feed.FeedConstants.KEY_FEED_URL;

public class Feed {

	final private String url;
	final private GroupId blogId;
	final private String title, description, author;
	final private long added, updated, lastEntryTime;

	public Feed(String url, GroupId blogId, @Nullable String title,
			@Nullable String description, @Nullable String author,
			long added, long updated, long lastEntryTime) {

		this.url = url;
		this.blogId = blogId;
		this.title = title;
		this.description = description;
		this.author = author;
		this.added = added;
		this.updated = updated;
		this.lastEntryTime = lastEntryTime;
	}

	public Feed(String url, GroupId blogId, @Nullable String title,
			@Nullable String description, @Nullable String author,
			long added) {

		this(url, blogId, title, description, author, added, 0L, 0L);
	}

	public Feed(String url, GroupId blogId, long added) {

		this(url, blogId, null, null, null, added, 0L, 0L);
	}

	public String getUrl() {
		return url;
	}

	public GroupId getBlogId() {
		return blogId;
	}

	public BdfDictionary toBdfDictionary() {
		BdfDictionary d = BdfDictionary.of(
				new BdfEntry(KEY_FEED_URL, url),
				new BdfEntry(KEY_BLOG_GROUP_ID, blogId.getBytes()),
				new BdfEntry(KEY_FEED_ADDED, added),
				new BdfEntry(KEY_FEED_UPDATED, updated),
				new BdfEntry(KEY_FEED_LAST_ENTRY, lastEntryTime)
		);
		if (title != null) d.put(KEY_FEED_TITLE, title);
		if (description != null) d.put(KEY_FEED_DESC, description);
		if (author != null) d.put(KEY_FEED_AUTHOR, author);
		return d;
	}

	public static Feed from(BdfDictionary d) throws FormatException {
		String url = d.getString(KEY_FEED_URL);
		GroupId blogId = new GroupId(d.getRaw(KEY_BLOG_GROUP_ID));
		String title = d.getOptionalString(KEY_FEED_TITLE);
		String desc = d.getOptionalString(KEY_FEED_DESC);
		String author = d.getOptionalString(KEY_FEED_AUTHOR);
		long added = d.getLong(KEY_FEED_ADDED, 0L);
		long updated = d.getLong(KEY_FEED_UPDATED, 0L);
		long lastEntryTime = d.getLong(KEY_FEED_LAST_ENTRY, 0L);
		return new Feed(url, blogId, title, desc, author, added, updated,
				lastEntryTime);
	}

	@Nullable
	public String getTitle() {
		return title;
	}

	@Nullable
	public String getDescription() {
		return description;
	}

	@Nullable
	public String getAuthor() {
		return author;
	}

	public long getAdded() {
		return added;
	}

	public long getUpdated() {
		return updated;
	}

	public long getLastEntryTime() {
		return lastEntryTime;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o instanceof Feed) {
			Feed f = (Feed) o;
			return url.equals(f.url) && blogId.equals(f.getBlogId()) &&
					equalsWithNull(title, f.getTitle()) &&
					equalsWithNull(description, f.getDescription()) &&
					equalsWithNull(author, f.getAuthor()) &&
					added == f.getAdded() &&
					updated == f.getUpdated() &&
					lastEntryTime == f.getLastEntryTime();
		}
		return false;
	}

	private boolean equalsWithNull(Object a, Object b) {
		if (a == b) return true;
		if (a == null || b==null) return false;
		return a.equals(b);
	}
}
