package org.briarproject.briar.headless.blogs;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.identity.OutputAuthor;
import org.briarproject.briar.api.blog.BlogPostHeader;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
@SuppressWarnings("WeakerAccess")
class OutputBlogPost {

	public final String body;
	public final OutputAuthor author;
	public final String authorStatus, type;
	public final byte[] id;
	@Nullable
	public final byte[] parentId;
	public final boolean read, rssFeed;
	public final long timestamp, timestampReceived;

	OutputBlogPost(BlogPostHeader header, String body) {
		this.body = body;
		this.author = new OutputAuthor(header.getAuthor());
		this.authorStatus = header.getAuthorStatus().name().toLowerCase();
		this.type = header.getType().name().toLowerCase();
		this.id = header.getId().getBytes();
		this.parentId = header.getParentId() == null ? null :
				header.getParentId().getBytes();
		this.read = header.isRead();
		this.rssFeed = header.isRssFeed();
		this.timestamp = header.getTimestamp();
		this.timestampReceived = header.getTimeReceived();
	}

}
