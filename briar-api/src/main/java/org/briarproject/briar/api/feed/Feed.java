package org.briarproject.briar.api.feed;

import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class Feed implements Comparable<Feed> {

	private final Blog blog;
	private final LocalAuthor localAuthor;
	private final RssProperties properties;
	private final long added, updated, lastEntryTime;

	public Feed(Blog blog, LocalAuthor localAuthor, RssProperties properties,
			long added, long updated, long lastEntryTime) {
		this.blog = blog;
		this.localAuthor = localAuthor;
		this.properties = properties;
		this.added = added;
		this.updated = updated;
		this.lastEntryTime = lastEntryTime;
	}

	public GroupId getBlogId() {
		return blog.getId();
	}

	public Blog getBlog() {
		return blog;
	}

	public LocalAuthor getLocalAuthor() {
		return localAuthor;
	}

	public String getTitle() {
		return blog.getName();
	}

	public RssProperties getProperties() {
		return properties;
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
			return blog.equals(f.blog);
		}
		return false;
	}

	// FIXME: compareTo() is inconsistent with equals()
	@Override
	public int compareTo(Feed o) {
		if (this == o) return 0;
		long aTime = getAdded(), bTime = o.getAdded();
		if (aTime > bTime) return -1;
		if (aTime < bTime) return 1;
		return 0;
	}

	@Override
	public int hashCode() {
		return blog.hashCode();
	}
}
