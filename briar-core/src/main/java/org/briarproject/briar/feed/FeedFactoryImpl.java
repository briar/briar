package org.briarproject.briar.feed;

import com.rometools.rome.feed.synd.SyndFeed;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.SignaturePrivateKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogFactory;
import org.briarproject.briar.api.feed.Feed;
import org.briarproject.briar.api.feed.RssProperties;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.util.StringUtils.truncateUtf8;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEED_ADDED;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEED_AUTHOR;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEED_DESC;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEED_LAST_ENTRY;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEED_PRIVATE_KEY;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEED_RSS_AUTHOR;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEED_RSS_LINK;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEED_RSS_TITLE;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEED_RSS_URI;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEED_UPDATED;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEED_URL;

class FeedFactoryImpl implements FeedFactory {

	private final AuthorFactory authorFactory;
	private final BlogFactory blogFactory;
	private final ClientHelper clientHelper;
	private final Clock clock;

	@Inject
	FeedFactoryImpl(AuthorFactory authorFactory, BlogFactory blogFactory,
			ClientHelper clientHelper, Clock clock) {
		this.authorFactory = authorFactory;
		this.blogFactory = blogFactory;
		this.clientHelper = clientHelper;
		this.clock = clock;
	}

	@Override
	public Feed createFeed(@Nullable String url, SyndFeed sf) {
		String title = sf.getTitle();
		if (title == null) title = "RSS";
		else title = truncateUtf8(title, MAX_AUTHOR_NAME_LENGTH);

		LocalAuthor localAuthor = authorFactory.createLocalAuthor(title);
		Blog blog = blogFactory.createFeedBlog(localAuthor);
		long added = clock.currentTimeMillis();

		RssProperties properties = new RssProperties(url, sf.getTitle(),
				sf.getDescription(), sf.getAuthor(), sf.getLink(), sf.getUri());
		return new Feed(blog, localAuthor, properties, added, 0, 0);
	}

	@Override
	public Feed updateFeed(Feed feed, SyndFeed sf, long lastEntryTime) {
		long updated = clock.currentTimeMillis();
		String url = feed.getProperties().getUrl();
		// Update the RSS properties
		RssProperties properties = new RssProperties(url, sf.getTitle(),
				sf.getDescription(), sf.getAuthor(), sf.getLink(), sf.getUri());
		return new Feed(feed.getBlog(), feed.getLocalAuthor(), properties,
				feed.getAdded(), updated, lastEntryTime);
	}

	@Override
	public Feed createFeed(BdfDictionary d) throws FormatException {

		BdfList authorList = d.getList(KEY_FEED_AUTHOR);
		PrivateKey privateKey =
				new SignaturePrivateKey(d.getRaw(KEY_FEED_PRIVATE_KEY));
		Author author = clientHelper.parseAndValidateAuthor(authorList);
		LocalAuthor localAuthor = new LocalAuthor(author.getId(),
				author.getFormatVersion(), author.getName(),
				author.getPublicKey(), privateKey);
		Blog blog = blogFactory.createFeedBlog(localAuthor);

		String url = d.getOptionalString(KEY_FEED_URL);
		String description = d.getOptionalString(KEY_FEED_DESC);
		String rssAuthor = d.getOptionalString(KEY_FEED_RSS_AUTHOR);
		String title = d.getOptionalString(KEY_FEED_RSS_TITLE);
		String link = d.getOptionalString(KEY_FEED_RSS_LINK);
		String uri = d.getOptionalString(KEY_FEED_RSS_URI);
		RssProperties properties = new RssProperties(url, title, description,
				rssAuthor, link, uri);

		long added = d.getLong(KEY_FEED_ADDED, 0L);
		long updated = d.getLong(KEY_FEED_UPDATED, 0L);
		long lastEntryTime = d.getLong(KEY_FEED_LAST_ENTRY, 0L);

		return new Feed(blog, localAuthor, properties, added, updated,
				lastEntryTime);
	}

	@Override
	public BdfDictionary feedToBdfDictionary(Feed feed) {
		LocalAuthor localAuthor = feed.getLocalAuthor();
		BdfList authorList = clientHelper.toList(localAuthor);
		BdfDictionary d = BdfDictionary.of(
				new BdfEntry(KEY_FEED_AUTHOR, authorList),
				new BdfEntry(KEY_FEED_PRIVATE_KEY, localAuthor.getPrivateKey()),
				new BdfEntry(KEY_FEED_ADDED, feed.getAdded()),
				new BdfEntry(KEY_FEED_UPDATED, feed.getUpdated()),
				new BdfEntry(KEY_FEED_LAST_ENTRY, feed.getLastEntryTime())
		);
		RssProperties properties = feed.getProperties();
		if (properties.getUrl() != null)
			d.put(KEY_FEED_URL, properties.getUrl());
		if (properties.getTitle() != null)
			d.put(KEY_FEED_RSS_TITLE, properties.getTitle());
		if (properties.getDescription() != null)
			d.put(KEY_FEED_DESC, properties.getDescription());
		if (properties.getAuthor() != null)
			d.put(KEY_FEED_RSS_AUTHOR, properties.getAuthor());
		if (properties.getLink() != null)
			d.put(KEY_FEED_RSS_LINK, properties.getLink());
		if (properties.getUri() != null)
			d.put(KEY_FEED_RSS_URI, properties.getUri());
		return d;
	}

}
