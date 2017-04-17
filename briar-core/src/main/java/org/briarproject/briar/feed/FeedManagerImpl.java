package org.briarproject.briar.feed;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.event.TransportEnabledEvent;
import org.briarproject.bramble.api.sync.Client;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.Scheduler;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogPost;
import org.briarproject.briar.api.blog.BlogPostFactory;
import org.briarproject.briar.api.feed.Feed;
import org.briarproject.briar.api.feed.FeedManager;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.net.SocketFactory;

import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.WARNING;
import static org.briarproject.briar.api.blog.BlogConstants.MAX_BLOG_POST_BODY_LENGTH;
import static org.briarproject.briar.api.feed.FeedConstants.FETCH_DELAY_INITIAL;
import static org.briarproject.briar.api.feed.FeedConstants.FETCH_INTERVAL;
import static org.briarproject.briar.api.feed.FeedConstants.FETCH_UNIT;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEEDS;
import static org.briarproject.briar.util.HtmlUtils.ARTICLE;
import static org.briarproject.briar.util.HtmlUtils.STRIP_ALL;
import static org.briarproject.briar.util.HtmlUtils.clean;

@ThreadSafe
@NotNullByDefault
class FeedManagerImpl implements FeedManager, Client, EventListener,
		BlogManager.RemoveBlogHook {

	private static final Logger LOG =
			Logger.getLogger(FeedManagerImpl.class.getName());

	private static final int CONNECT_TIMEOUT = 60 * 1000; // Milliseconds

	private final ScheduledExecutorService scheduler;
	private final Executor ioExecutor;
	private final DatabaseComponent db;
	private final ContactGroupFactory contactGroupFactory;
	private final ClientHelper clientHelper;
	private final BlogManager blogManager;
	private final BlogPostFactory blogPostFactory;
	private final FeedFactory feedFactory;
	private final SocketFactory torSocketFactory;
	private final Clock clock;
	private final Dns noDnsLookups;
	private final AtomicBoolean fetcherStarted = new AtomicBoolean(false);

	@Inject
	FeedManagerImpl(@Scheduler ScheduledExecutorService scheduler,
			@IoExecutor Executor ioExecutor, DatabaseComponent db,
			ContactGroupFactory contactGroupFactory, ClientHelper clientHelper,
			BlogManager blogManager, BlogPostFactory blogPostFactory,
			FeedFactory feedFactory, SocketFactory torSocketFactory,
			Clock clock, Dns noDnsLookups) {

		this.scheduler = scheduler;
		this.ioExecutor = ioExecutor;
		this.db = db;
		this.contactGroupFactory = contactGroupFactory;
		this.clientHelper = clientHelper;
		this.blogManager = blogManager;
		this.blogPostFactory = blogPostFactory;
		this.feedFactory = feedFactory;
		this.torSocketFactory = torSocketFactory;
		this.clock = clock;
		this.noDnsLookups = noDnsLookups;
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof TransportEnabledEvent) {
			TransportId t = ((TransportEnabledEvent) e).getTransportId();
			if (t.equals(TorConstants.ID)) {
				startFeedExecutor();
			}
		}
	}

	private void startFeedExecutor() {
		if (fetcherStarted.getAndSet(true)) return;
		LOG.info("Tor started, scheduling RSS feed fetcher");
		Runnable fetcher = new Runnable() {
			@Override
			public void run() {
				ioExecutor.execute(new Runnable() {
					@Override
					public void run() {
						fetchFeeds();
					}
				});
			}
		};
		scheduler.scheduleWithFixedDelay(fetcher, FETCH_DELAY_INITIAL,
				FETCH_INTERVAL, FETCH_UNIT);
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
	public void addFeed(String url) throws DbException, IOException {
		// fetch syndication feed to get its metadata
		SyndFeed f;
		try {
			f = fetchSyndFeed(url);
		} catch (FeedException e) {
			throw new IOException(e);
		}

		Feed feed = feedFactory.createFeed(url, f);

		// store feed and new blog
		Transaction txn = db.startTransaction(false);
		try {
			blogManager.addBlog(txn, feed.getBlog());
			List<Feed> feeds = getFeeds(txn);
			feeds.add(feed);
			storeFeeds(txn, feeds);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}

		// fetch feed again and post entries
		Feed updatedFeed;
		try {
			updatedFeed = fetchFeed(feed);
		} catch (FeedException e) {
			throw new IOException(e);
		}

		// store feed again to also store last added entry
		txn = db.startTransaction(false);
		try {
			List<Feed> feeds = getFeeds(txn);
			feeds.remove(feed);
			feeds.add(updatedFeed);
			storeFeeds(txn, feeds);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void removeFeed(Feed feed) throws DbException {
		LOG.info("Removing RSS feed...");
		Transaction txn = db.startTransaction(false);
		try {
			// this will call removingBlog() where the feed itself gets removed
			blogManager.removeBlog(txn, feed.getBlog());
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void removingBlog(Transaction txn, Blog b) throws DbException {
		if (!b.isRssFeed()) return;

		// delete blog's RSS feed if we have it
		boolean found = false;
		List<Feed> feeds = getFeeds(txn);
		for (Feed f : feeds) {
			if (f.getBlogId().equals(b.getId())) {
				found = true;
				feeds.remove(f);
				break;
			}
		}
		if (found) storeFeeds(txn, feeds);
	}

	@Override
	public List<Feed> getFeeds() throws DbException {
		List<Feed> feeds;
		Transaction txn = db.startTransaction(true);
		try {
			feeds = getFeeds(txn);
			db.commitTransaction(txn);
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
				feeds.add(feedFactory.createFeed((BdfDictionary) object));
			}
		} catch (FormatException e) {
			throw new DbException(e);
		}
		return feeds;
	}

	private void storeFeeds(@Nullable Transaction txn, List<Feed> feeds)
			throws DbException {

		BdfList feedList = new BdfList();
		for (Feed feed : feeds) {
			feedList.add(feedFactory.feedToBdfDictionary(feed));
		}
		BdfDictionary gm = BdfDictionary.of(new BdfEntry(KEY_FEEDS, feedList));
		try {
			if (txn == null) {
				clientHelper.mergeGroupMetadata(getLocalGroup().getId(), gm);
			} else {
				clientHelper.mergeGroupMetadata(txn, getLocalGroup().getId(),
						gm);
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
			} catch (DbException e) {
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

	private SyndFeed fetchSyndFeed(String url)
			throws FeedException, IOException {
		// fetch feed
		SyndFeed f = getSyndFeed(getFeedInputStream(url));

		if (f.getEntries().size() == 0)
			throw new FeedException("Feed has no entries");

		// clean title
		String title =
				StringUtils.isNullOrEmpty(f.getTitle()) ? null : f.getTitle();
		if (title != null) title = clean(title, STRIP_ALL);
		f.setTitle(title);

		// clean description
		String description =
				StringUtils.isNullOrEmpty(f.getDescription()) ? null :
						f.getDescription();
		if (description != null) description = clean(description, STRIP_ALL);
		f.setDescription(description);

		// clean author
		String author =
				StringUtils.isNullOrEmpty(f.getAuthor()) ? null : f.getAuthor();
		if (author != null) author = clean(author, STRIP_ALL);
		f.setAuthor(author);

		return f;
	}

	private Feed fetchFeed(Feed feed)
			throws FeedException, IOException, DbException {
		// fetch and clean feed
		SyndFeed f = fetchSyndFeed(feed.getUrl());

		// sort and add new entries
		long lastEntryTime = postFeedEntries(feed, f.getEntries());

		return feedFactory.createFeed(feed, f, lastEntryTime);
	}

	private InputStream getFeedInputStream(String url) throws IOException {
		// Build HTTP Client
		OkHttpClient client = new OkHttpClient.Builder()
				.socketFactory(torSocketFactory)
				.dns(noDnsLookups) // Don't make local DNS lookups
				.connectTimeout(CONNECT_TIMEOUT, MILLISECONDS)
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

	private long postFeedEntries(Feed feed, List<SyndEntry> entries)
			throws DbException {

		long lastEntryTime = feed.getLastEntryTime();
		Transaction txn = db.startTransaction(false);
		try {
			Collections.sort(entries, getEntryComparator());
			for (SyndEntry entry : entries) {
				long entryTime;
				if (entry.getPublishedDate() != null) {
					entryTime = entry.getPublishedDate().getTime();
				} else if (entry.getUpdatedDate() != null) {
					entryTime = entry.getUpdatedDate().getTime();
				} else {
					// no time information available, ignore this entry
					LOG.warning("Entry has no date, ignored.");
					continue;
				}
				if (entryTime > feed.getLastEntryTime()) {
					postEntry(txn, feed, entry);
					if (entryTime > lastEntryTime) lastEntryTime = entryTime;
				}
			}
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return lastEntryTime;
	}

	private void postEntry(Transaction txn, Feed feed, SyndEntry entry)
			throws DbException {
		LOG.info("Adding new entry...");

		// build post body
		StringBuilder b = new StringBuilder();
		b.append("<h3>").append(feed.getTitle()).append("</h3>");

		if (!StringUtils.isNullOrEmpty(entry.getTitle())) {
			b.append("<h1>").append(entry.getTitle()).append("</h1>");
		}
		for (SyndContent content : entry.getContents()) {
			if (content.getValue() != null)
				b.append(content.getValue());
		}
		if (entry.getContents().size() == 0) {
			if (entry.getDescription() != null &&
					entry.getDescription().getValue() != null)
				b.append(entry.getDescription().getValue());
		}
		b.append("<p>");
		if (!StringUtils.isNullOrEmpty(entry.getAuthor())) {
			b.append("-- ").append(entry.getAuthor());
		}
		if (entry.getPublishedDate() != null) {
			b.append(" (").append(entry.getPublishedDate().toString())
					.append(")");
		} else if (entry.getUpdatedDate() != null) {
			b.append(" (").append(entry.getUpdatedDate().toString())
					.append(")");
		}
		b.append("</p>");
		String link = entry.getLink();
		if (!StringUtils.isNullOrEmpty(link)) {
			b.append("<a href=\"").append(link).append("\">").append(link)
					.append("</a>");
		}

		// get other information for post
		GroupId groupId = feed.getBlogId();
		long time, now = clock.currentTimeMillis();
		Date date = entry.getUpdatedDate();
		if (date == null) date = entry.getPublishedDate();
		if (date == null) time = now;
		else time = Math.max(0, Math.min(date.getTime(), now));
		String body = getPostBody(b.toString());
		try {
			// create and store post
			LocalAuthor localAuthor = feed.getLocalAuthor();
			BlogPost post = blogPostFactory
					.createBlogPost(groupId, time, null, localAuthor, body);
			blogManager.addLocalPost(txn, post);
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

	private String getPostBody(String text) {
		text = clean(text, ARTICLE);
		return StringUtils.truncateUtf8(text, MAX_BLOG_POST_BODY_LENGTH);
	}

	/**
	 * This Comparator assumes that SyndEntry returns a valid Date either for
	 * getPublishedDate() or getUpdatedDate().
	 */
	private Comparator<SyndEntry> getEntryComparator() {
		return new Comparator<SyndEntry>() {
			@Override
			public int compare(SyndEntry e1, SyndEntry e2) {
				if (e1.getPublishedDate() == null &&
						e1.getUpdatedDate() == null) {
					// we will be ignoring such entries anyway
					return 0;
				}
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
		return contactGroupFactory.createLocalGroup(CLIENT_ID);
	}

}
