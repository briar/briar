package org.briarproject.briar.api.feed;

import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * The properties of an RSS feed, which may have been imported from a URL
 * or a file.
 */
@Immutable
@NotNullByDefault
public class RssProperties {

	@Nullable
	private final String url, title, description, author, link, uri;

	public RssProperties(@Nullable String url, @Nullable String title,
			@Nullable String description, @Nullable String author,
			@Nullable String link, @Nullable String uri) {
		this.url = url;
		this.title = title;
		this.description = description;
		this.author = author;
		this.link = link;
		this.uri = uri;
	}

	/**
	 * Returns the URL from which the RSS feed was imported, or null if the
	 * feed was imported from a file.
	 */
	@Nullable
	public String getUrl() {
		return url;
	}

	/**
	 * Returns the title property of the RSS feed, or null if no title was
	 * specified.
	 */
	@Nullable
	public String getTitle() {
		return title;
	}

	/**
	 * Returns the description property of the RSS feed, or null if no
	 * description was specified.
	 */
	@Nullable
	public String getDescription() {
		return description;
	}

	/**
	 * Returns the author property of the RSS feed, or null if no author was
	 * specified.
	 */
	@Nullable
	public String getAuthor() {
		return author;
	}

	/**
	 * Returns the link property of the RSS feed, or null if no link was
	 * specified. This is usually the URL of a webpage where the equivalent
	 * content can be viewed in a browser.
	 */
	@Nullable
	public String getLink() {
		return link;
	}

	/**
	 * Returns the URI property of the RSS feed, or null if no URI was
	 * specified. This may be a URL from which the feed can be downloaded,
	 * or it may be an opaque identifier such as a number that serves to
	 * distinguish this feed from other feeds produced by the same creator.
	 */
	@Nullable
	public String getUri() {
		return uri;
	}
}
