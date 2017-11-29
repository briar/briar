package org.briarproject.briar.feed;

import com.rometools.rome.feed.synd.SyndFeed;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogFactory;
import org.briarproject.briar.api.feed.Feed;

import javax.inject.Inject;

import static org.briarproject.briar.api.blog.BlogConstants.MAX_BLOG_NAME_LENGTH;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_BLOG_TITLE;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEED_ADDED;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEED_AUTHOR;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEED_DESC;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEED_LAST_ENTRY;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEED_UPDATED;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEED_URL;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FORMAT_VERSION;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_PRIVATE_KEY;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_PUBLIC_KEY;

class FeedFactoryImpl implements FeedFactory {

	private final CryptoComponent cryptoComponent;
	private final AuthorFactory authorFactory;
	private final BlogFactory blogFactory;
	private final Clock clock;

	@Inject
	FeedFactoryImpl(CryptoComponent cryptoComponent,
			AuthorFactory authorFactory, BlogFactory blogFactory, Clock clock) {
		this.cryptoComponent = cryptoComponent;
		this.authorFactory = authorFactory;
		this.blogFactory = blogFactory;
		this.clock = clock;
	}

	@Override
	public Feed createFeed(String url, SyndFeed syndFeed) {
		String title = syndFeed.getTitle();
		if (title == null) title = "RSS";
		else title = StringUtils.truncateUtf8(title, MAX_BLOG_NAME_LENGTH);

		KeyPair keyPair = cryptoComponent.generateSignatureKeyPair();
		LocalAuthor localAuthor = authorFactory.createLocalAuthor(title,
				keyPair.getPublic().getEncoded(),
				keyPair.getPrivate().getEncoded());
		Blog blog = blogFactory.createFeedBlog(localAuthor);
		long added = clock.currentTimeMillis();

		return new Feed(url, blog, localAuthor, added);
	}

	@Override
	public Feed createFeed(Feed feed, SyndFeed f, long lastEntryTime) {
		long updated = clock.currentTimeMillis();
		return new Feed(feed.getUrl(), feed.getBlog(), feed.getLocalAuthor(),
				f.getDescription(), f.getAuthor(), feed.getAdded(), updated,
				lastEntryTime);
	}

	@Override
	public Feed createFeed(BdfDictionary d) throws FormatException {
		String url = d.getString(KEY_FEED_URL);

		int formatVersion = d.getLong(KEY_FORMAT_VERSION).intValue();
		String blogTitle = d.getString(KEY_BLOG_TITLE);
		byte[] publicKey = d.getRaw(KEY_PUBLIC_KEY);
		byte[] privateKey = d.getRaw(KEY_PRIVATE_KEY);
		LocalAuthor localAuthor = authorFactory.createLocalAuthor(
				formatVersion, blogTitle, publicKey, privateKey);
		Blog blog = blogFactory.createFeedBlog(localAuthor);

		String desc = d.getOptionalString(KEY_FEED_DESC);
		String author = d.getOptionalString(KEY_FEED_AUTHOR);
		long added = d.getLong(KEY_FEED_ADDED, 0L);
		long updated = d.getLong(KEY_FEED_UPDATED, 0L);
		long lastEntryTime = d.getLong(KEY_FEED_LAST_ENTRY, 0L);

		return new Feed(url, blog, localAuthor, desc, author, added,
				updated, lastEntryTime);
	}

	@Override
	public BdfDictionary feedToBdfDictionary(Feed feed) {
		LocalAuthor localAuthor = feed.getLocalAuthor();
		BdfDictionary d = BdfDictionary.of(
				new BdfEntry(KEY_FEED_URL, feed.getUrl()),
				new BdfEntry(KEY_FORMAT_VERSION,
						localAuthor.getFormatVersion()),
				new BdfEntry(KEY_BLOG_TITLE, localAuthor.getName()),
				new BdfEntry(KEY_PUBLIC_KEY, localAuthor.getPublicKey()),
				new BdfEntry(KEY_PRIVATE_KEY, localAuthor.getPrivateKey()),
				new BdfEntry(KEY_FEED_ADDED, feed.getAdded()),
				new BdfEntry(KEY_FEED_UPDATED, feed.getUpdated()),
				new BdfEntry(KEY_FEED_LAST_ENTRY, feed.getLastEntryTime())
		);
		if (feed.getDescription() != null)
			d.put(KEY_FEED_DESC, feed.getDescription());
		if (feed.getAuthor() != null) d.put(KEY_FEED_AUTHOR, feed.getAuthor());
		return d;
	}

}
