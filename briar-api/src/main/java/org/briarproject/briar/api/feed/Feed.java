package org.briarproject.briar.api.feed;

import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.blog.Blog;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class Feed {

	private final String url;
	private final Blog blog;
	private final LocalAuthor localAuthor;
	@Nullable
	private final String title, description, author;
	private final long added, updated, lastEntryTime;

	public Feed(String url, Blog blog, LocalAuthor localAuthor,
			@Nullable String title, @Nullable String description,
			@Nullable String author, long added, long updated,
			long lastEntryTime) {

		this.url = url;
		this.blog = blog;
		this.localAuthor = localAuthor;
		this.title = title;
		this.description = description;
		this.author = author;
		this.added = added;
		this.updated = updated;
		this.lastEntryTime = lastEntryTime;
	}

	public Feed(String url, Blog blog, LocalAuthor localAuthor,
			@Nullable String title, @Nullable String description,
			@Nullable String author, long added) {
		this(url, blog, localAuthor, title, description, author, added, 0L, 0L);
	}

	public Feed(String url, Blog blog, LocalAuthor localAuthor, long added) {
		this(url, blog, localAuthor, null, null, null, added, 0L, 0L);
	}

	public String getUrl() {
		return url;
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
			return blog.equals(f.blog);
		}
		return false;
	}

}
