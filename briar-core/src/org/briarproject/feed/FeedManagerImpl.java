package org.briarproject.feed;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import org.briarproject.api.FormatException;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogPost;
import org.briarproject.api.blogs.BlogPostFactory;
import org.briarproject.api.clients.Client;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.PrivateGroupFactory;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.feed.Feed;
import org.briarproject.api.feed.FeedManager;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.lifecycle.ServiceException;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.system.Clock;
import org.briarproject.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import javax.inject.Inject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.blogs.BlogConstants.MAX_BLOG_POST_BODY_LENGTH;
import static org.briarproject.api.feed.FeedConstants.FETCH_DELAY_INITIAL;
import static org.briarproject.api.feed.FeedConstants.FETCH_INTERVAL;
import static org.briarproject.api.feed.FeedConstants.FETCH_UNIT;
import static org.briarproject.api.feed.FeedConstants.KEY_FEEDS;

class FeedManagerImpl implements FeedManager, Service, Client {

	private static final Logger LOG =
			Logger.getLogger(FeedManagerImpl.class.getName());

	private static final ClientId CLIENT_ID =
			new ClientId(StringUtils.fromHexString(
					"466565644d616e6167657202fb797097"
							+ "255af837abbf8c16e250b3c2ccc286eb"));

	private final ScheduledExecutorService feedExecutor;
	private final Executor ioExecutor;
	private final DatabaseComponent db;
	private final PrivateGroupFactory privateGroupFactory;
	private final ClientHelper clientHelper;
	private IdentityManager identityManager;
	private final BlogManager blogManager;

	@Inject
	@SuppressWarnings("WeakerAccess")
	BlogPostFactory blogPostFactory;
	@Inject
	@SuppressWarnings("WeakerAccess")
	Clock clock;

	@Inject
	FeedManagerImpl(ScheduledExecutorService feedExecutor,
			@IoExecutor Executor ioExecutor, DatabaseComponent db,
			PrivateGroupFactory privateGroupFactory, ClientHelper clientHelper,
			IdentityManager identityManager, BlogManager blogManager) {

		this.feedExecutor = feedExecutor;
		this.ioExecutor = ioExecutor;
		this.db = db;
		this.privateGroupFactory = privateGroupFactory;
		this.clientHelper = clientHelper;
		this.identityManager = identityManager;
		this.blogManager = blogManager;
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public void startService() throws ServiceException {
		Runnable fetcher = new Runnable() {
			public void run() {
				ioExecutor.execute(new Runnable() {
					@Override
					public void run() {
						fetchFeeds();
					}
				});
			}
		};
		feedExecutor.scheduleWithFixedDelay(fetcher, FETCH_DELAY_INITIAL,
				FETCH_INTERVAL, FETCH_UNIT);
	}

	@Override
	public void stopService() throws ServiceException {
		// feedExecutor will be stopped by LifecycleManager
	}

	@Override
	public void createLocalState(Transaction txn) throws DbException {
		Group g = getLocalGroup();
		// Return if we've already set the local group up
		if (db.containsGroup(txn, g.getId())) return;

		// Store the group
		db.addGroup(txn, g);

		// Add initial metadata
		List<Feed> feeds = new ArrayList<Feed>(0);
		storeFeeds(txn, feeds);
	}

	@Override
	public void addFeed(String url, GroupId g) throws DbException, IOException {
		LOG.info("Adding new RSS feed...");

		// TODO check for existing feed?
		Feed feed = new Feed(url, g, clock.currentTimeMillis());
		try {
			feed = fetchFeed(feed);
		} catch (FeedException e) {
			throw new IOException(e);
		}

		Transaction txn = db.startTransaction(false);
		try {
			List<Feed> feeds = getFeeds(txn);
			feeds.add(feed);
			storeFeeds(txn, feeds);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void removeFeed(String url) throws DbException {
		LOG.info("Removing RSS feed...");
		Transaction txn = db.startTransaction(false);
		try {
			List<Feed> feeds = getFeeds(txn);
			boolean found = false;
			for (Feed feed : feeds) {
				if (feed.getUrl().equals(url)) {
					found = true;
					feeds.remove(feed);
					break;
				}
			}
			if (!found) throw new DbException();
			storeFeeds(txn, feeds);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public List<Feed> getFeeds() throws DbException {
		List<Feed> feeds;
		Transaction txn = db.startTransaction(true);
		try {
			feeds = getFeeds(txn);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return feeds;
	}

	private List<Feed> getFeeds(Transaction txn) throws DbException {
		List<Feed> feeds = new ArrayList<Feed>();
		Group g = getLocalGroup();
		try {
			BdfDictionary d =
					clientHelper.getGroupMetadataAsDictionary(txn, g.getId());
			for (Object object : d.getList(KEY_FEEDS)) {
				if (!(object instanceof BdfDictionary))
					throw new FormatException();
				feeds.add(Feed.from((BdfDictionary) object));
			}
		} catch (FormatException e) {
			throw new DbException(e);
		}
		return feeds;
	}

	private void storeFeeds(Transaction txn, List<Feed> feeds)
			throws DbException {

		BdfList feedList = new BdfList();
		for (Feed feed : feeds) {
			feedList.add(feed.toBdfDictionary());
		}
		BdfDictionary gm = BdfDictionary.of(new BdfEntry(KEY_FEEDS, feedList));
		try {
			if (txn == null) {
				clientHelper.mergeGroupMetadata(getLocalGroup().getId(), gm);
			} else {
				clientHelper
						.mergeGroupMetadata(txn, getLocalGroup().getId(), gm);
			}
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private void storeFeeds(List<Feed> feeds) throws DbException {
		storeFeeds(null, feeds);
	}

	/**
	 * This method is called periodically from a background service.
	 * It fetches all available feeds and posts new entries to the respective
	 * blog.
	 */
	private void fetchFeeds() {
		LOG.info("Updating RSS feeds...");

		// Get current feeds
		List<Feed> feeds;
		try {
			feeds = getFeeds();
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING))
				LOG.log(WARNING, e.toString(), e);
			return;
		}

		// Fetch and update all feeds
		List<Feed> newFeeds = new ArrayList<Feed>(feeds.size());
		for (Feed feed : feeds) {
			try {
				newFeeds.add(fetchFeed(feed));
			} catch (FeedException e) {
				if (LOG.isLoggable(WARNING))
					LOG.log(WARNING, e.toString(), e);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING))
					LOG.log(WARNING, e.toString(), e);
			}
		}

		// Store updated feeds
		try {
			storeFeeds(newFeeds);
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING))
				LOG.log(WARNING, e.toString(), e);
		}
		LOG.info("Done updating RSS feeds");
	}

	private Feed fetchFeed(Feed feed) throws FeedException, IOException {
		if (LOG.isLoggable(INFO))
			LOG.info("Fetching feed from " + feed.getUrl());

		String title, description, author;
		long updated = clock.currentTimeMillis();
		long lastEntryTime = feed.getLastEntryTime();

		SyndFeed f = getSyndFeed(getFeedInputStream(feed.getUrl()));
		title = StringUtils.isNullOrEmpty(f.getTitle()) ? null : f.getTitle();
		if (title != null) title = stripHTML(title);
		description = StringUtils.isNullOrEmpty(f.getDescription()) ? null :
				f.getDescription();
		if (description != null) description = stripHTML(description);
		author =
				StringUtils.isNullOrEmpty(f.getAuthor()) ? null : f.getAuthor();
		if (author != null) author = stripHTML(author);

		// sort and add new entries
		Collections.sort(f.getEntries(), getEntryComparator());
		for (SyndEntry entry : f.getEntries()) {
			long entryTime;
			if (entry.getPublishedDate() != null) {
				entryTime = entry.getPublishedDate().getTime();
			} else if (entry.getUpdatedDate() != null) {
				entryTime = entry.getUpdatedDate().getTime();
			} else {
				// no time information available, ignore this entry
				if (LOG.isLoggable(WARNING))
					LOG.warning("Entry has no date: " + entry.getTitle());
				continue;
			}
			if (entryTime > feed.getLastEntryTime()) {
				postEntry(feed, entry);
				if (entryTime > lastEntryTime) lastEntryTime = entryTime;
			}
		}
		return new Feed(feed.getUrl(), feed.getBlogId(), title, description,
				author, feed.getAdded(), updated, lastEntryTime);
	}

	private InputStream getFeedInputStream(String url) throws IOException {
		// Set proxy
		// TODO verify and use local Tor proxy address/port
		String proxyHost = "localhost";
		int proxyPort = 59050;
		Proxy proxy = new Proxy(Proxy.Type.SOCKS,
				new InetSocketAddress(proxyHost, proxyPort));

		// Build HTTP Client
		OkHttpClient client = new OkHttpClient.Builder()
//				.proxy(proxy)
				.build();

		// Build Request
		Request request = new Request.Builder()
				.url(url)
				.build();

		// Execute Request
		Response response = client.newCall(request).execute();
		return response.body().byteStream();
	}

	private SyndFeed getSyndFeed(InputStream stream)
			throws IOException, FeedException {

		SyndFeedInput input = new SyndFeedInput();
		return input.build(new XmlReader(stream));
	}

	private void postEntry(Feed feed, SyndEntry entry) {
		LOG.info("Adding new entry...");

		// build post body
		StringBuilder b = new StringBuilder();
		if (!StringUtils.isNullOrEmpty(entry.getTitle())) {
			b.append(stripHTML(entry.getTitle())).append("\n\n");
		}
		for (SyndContent content : entry.getContents()) {
			// extract content and do a very simple HTML tag stripping
			if (content.getValue() != null)
				b.append(stripHTML(content.getValue()));
		}
		if (entry.getContents().size() == 0) {
			if (entry.getDescription().getValue() != null)
				b.append(stripHTML(entry.getDescription().getValue()));
		}
		if (!StringUtils.isNullOrEmpty(entry.getAuthor())) {
			b.append("\n\n-- ").append(stripHTML(entry.getAuthor()));
		}
		if (entry.getPublishedDate() != null) {
			b.append(" (").append(entry.getPublishedDate().toString())
					.append(")");
		} else if (entry.getUpdatedDate() != null) {
			b.append(" (").append(entry.getUpdatedDate().toString())
					.append(")");
		}
		if (!StringUtils.isNullOrEmpty(entry.getLink())) {
			b.append("\n\n").append(stripHTML(entry.getLink()));
		}

		// get other information for post
		GroupId groupId = feed.getBlogId();
		long time = clock.currentTimeMillis();
		byte[] body = getPostBody(b.toString());
		try {
			// create and store post
			Blog blog = blogManager.getBlog(groupId);
			AuthorId authorId = blog.getAuthor().getId();
			LocalAuthor author = identityManager.getLocalAuthor(authorId);
			BlogPost post = blogPostFactory
					.createBlogPost(groupId, null, time, null, author,
							"text/plain", body);
			blogManager.addLocalPost(post);
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING))
				LOG.log(WARNING, e.toString(), e);
		} catch (GeneralSecurityException e) {
			if (LOG.isLoggable(WARNING))
				LOG.log(WARNING, e.toString(), e);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING))
				LOG.log(WARNING, e.toString(), e);
		} catch (IllegalArgumentException e) {
			// yes even catch this, so we at least get a stacktrace
			// and the executor doesn't just die a silent death
			if (LOG.isLoggable(WARNING))
				LOG.log(WARNING, e.toString(), e);
		}
	}

	private String stripHTML(String s) {
		return StringUtils.trim(s.replaceAll("<.*?>", ""));
	}

	private byte[] getPostBody(String text) {
		byte[] body = StringUtils.toUtf8(text);
		if (body.length <= MAX_BLOG_POST_BODY_LENGTH) return body;
		else return Arrays.copyOfRange(body, 0, MAX_BLOG_POST_BODY_LENGTH - 1);
	}

	/**
	 * This Comparator assumes that SyndEntry returns a valid Date either for
	 * getPublishedDate() or getUpdatedDate().
	 */
	private Comparator<SyndEntry> getEntryComparator() {
		return new Comparator<SyndEntry>() {
			@Override
			public int compare(SyndEntry e1, SyndEntry e2) {
				Date d1 =
						e1.getPublishedDate() != null ? e1.getPublishedDate() :
								e1.getUpdatedDate();
				Date d2 =
						e2.getPublishedDate() != null ? e2.getPublishedDate() :
								e2.getUpdatedDate();
				if (d1.after(d2)) return 1;
				if (d1.before(d2)) return -1;
				return 0;
			}
		};
	}

	private Group getLocalGroup() {
		return privateGroupFactory.createLocalGroup(getClientId());
	}

}
