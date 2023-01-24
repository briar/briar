package org.briarproject.briar.feed;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.WeakSingletonProvider;
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
import org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.event.TransportActiveEvent;
import org.briarproject.bramble.api.plugin.event.TransportInactiveEvent;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.bramble.api.system.Wakeful;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogManager.RemoveBlogHook;
import org.briarproject.briar.api.blog.BlogPost;
import org.briarproject.briar.api.blog.BlogPostFactory;
import org.briarproject.briar.api.feed.Feed;
import org.briarproject.briar.api.feed.FeedManager;
import org.briarproject.briar.api.feed.RssProperties;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static java.util.Collections.singletonList;
import static java.util.Collections.sort;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.IoUtils.tryToClose;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;
import static org.briarproject.bramble.util.StringUtils.truncateUtf8;
import static org.briarproject.briar.api.blog.BlogConstants.MAX_BLOG_POST_TEXT_LENGTH;
import static org.briarproject.briar.api.feed.FeedConstants.FETCH_DELAY_INITIAL;
import static org.briarproject.briar.api.feed.FeedConstants.FETCH_INTERVAL;
import static org.briarproject.briar.api.feed.FeedConstants.FETCH_UNIT;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEEDS;
import static org.briarproject.briar.util.HtmlUtils.cleanAll;
import static org.briarproject.briar.util.HtmlUtils.cleanArticle;

@ThreadSafe
@NotNullByDefault
class FeedManagerImpl implements FeedManager, EventListener, OpenDatabaseHook,
		RemoveBlogHook {

	private static final Logger LOG =
			getLogger(FeedManagerImpl.class.getName());

	private final TaskScheduler scheduler;
	private final Executor ioExecutor;
	private final DatabaseComponent db;
	private final ContactGroupFactory contactGroupFactory;
	private final ClientHelper clientHelper;
	private final BlogManager blogManager;
	private final BlogPostFactory blogPostFactory;
	private final FeedFactory feedFactory;
	private final FeedMatcher feedMatcher;
	private final Clock clock;
	private final WeakSingletonProvider<OkHttpClient> httpClientProvider;
	private final AtomicBoolean fetcherStarted = new AtomicBoolean(false);

	private volatile boolean torActive = false;

	@Inject
	FeedManagerImpl(TaskScheduler scheduler,
			@IoExecutor Executor ioExecutor,
			DatabaseComponent db,
			ContactGroupFactory contactGroupFactory,
			ClientHelper clientHelper,
			BlogManager blogManager,
			BlogPostFactory blogPostFactory,
			FeedFactory feedFactory,
			FeedMatcher feedMatcher,
			WeakSingletonProvider<OkHttpClient> httpClientProvider,
			Clock clock) {
		this.scheduler = scheduler;
		this.ioExecutor = ioExecutor;
		this.db = db;
		this.contactGroupFactory = contactGroupFactory;
		this.clientHelper = clientHelper;
		this.blogManager = blogManager;
		this.blogPostFactory = blogPostFactory;
		this.feedFactory = feedFactory;
		this.feedMatcher = feedMatcher;
		this.httpClientProvider = httpClientProvider;
		this.clock = clock;
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof TransportActiveEvent) {
			TransportId t = ((TransportActiveEvent) e).getTransportId();
			if (t.equals(TorConstants.ID)) {
				setTorActive(true);
				startFeedExecutor();
			}
		} else if (e instanceof TransportInactiveEvent) {
			TransportId t = ((TransportInactiveEvent) e).getTransportId();
			if (t.equals(TorConstants.ID)) setTorActive(false);
		}
	}

	// Package access for testing
	void setTorActive(boolean active) {
		torActive = active;
	}

	private void startFeedExecutor() {
		if (fetcherStarted.getAndSet(true)) return;
		LOG.info("Tor started, scheduling RSS feed fetcher");
		scheduler.scheduleWithFixedDelay(this::fetchFeeds, ioExecutor,
				FETCH_DELAY_INITIAL, FETCH_INTERVAL, FETCH_UNIT);
	}

	@Override
	public void onDatabaseOpened(Transaction txn) throws DbException {
		Group g = getLocalGroup();
		// Return if we've already set the local group up
		if (db.containsGroup(txn, g.getId())) return;

		// Store the group
		db.addGroup(txn, g);

		// Add initial metadata
		List<Feed> feeds = new ArrayList<>(0);
		storeFeeds(txn, feeds);
	}

	@Override
	public Feed addFeed(String url) throws DbException, IOException {
		// fetch feed to get posts and metadata
		SyndFeed sf = fetchAndCleanFeed(url);
		return addFeed(url, sf);
	}

	@Override
	public Feed addFeed(InputStream in) throws DbException, IOException {
		// fetch feed to get posts and metadata
		SyndFeed sf = fetchAndCleanFeed(in);
		return addFeed(null, sf);
	}

	private Feed addFeed(@Nullable String url, SyndFeed sf) throws DbException {
		// extract properties from the feed
		RssProperties properties = new RssProperties(url, sf.getTitle(),
				sf.getDescription(), sf.getAuthor(), sf.getLink(), sf.getUri());

		// check whether the properties match an existing feed
		List<Feed> candidates = db.transactionWithResult(true, this::getFeeds);
		Feed matched = feedMatcher.findMatchingFeed(properties, candidates);

		Feed feed;
		if (matched == null) {
			LOG.info("Adding new feed");
			feed = feedFactory.createFeed(url, sf);
			// store feed metadata and new blog
			db.transaction(false, txn -> {
				blogManager.addBlog(txn, feed.getBlog());
				List<Feed> feeds = getFeeds(txn);
				feeds.add(feed);
				storeFeeds(txn, feeds);
			});
		} else {
			LOG.info("New feed matches an existing feed");
			feed = matched;
		}

		// post entries
		long lastEntryTime = postFeedEntries(feed, sf.getEntries());
		Feed updatedFeed = feedFactory.updateFeed(feed, sf, lastEntryTime);

		// store feed metadata again to also store last entry time
		updateFeeds(singletonList(updatedFeed));

		return updatedFeed;
	}

	@Override
	public void removeFeed(Feed feed) throws DbException {
		LOG.info("Removing RSS feed...");
		// this will call removingBlog() where the feed itself gets removed
		db.transaction(false, txn ->
				blogManager.removeBlog(txn, feed.getBlog()));
	}

	@Override
	public void removingBlog(Transaction txn, Blog b) throws DbException {
		if (!b.isRssFeed()) return;

		// delete blog's RSS feed if we have it
		boolean found = false;
		List<Feed> feeds = getFeeds(txn);
		Iterator<Feed> it = feeds.iterator();
		while (it.hasNext()) {
			Feed f = it.next();
			if (f.getBlogId().equals(b.getId())) {
				it.remove();
				found = true;
				break;
			}
		}
		if (found) storeFeeds(txn, feeds);
	}

	@Override
	public List<Feed> getFeeds() throws DbException {
		return db.transactionWithResult(true, this::getFeeds);
	}

	@Override
	public List<Feed> getFeeds(Transaction txn) throws DbException {
		List<Feed> feeds = new ArrayList<>();
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

	private void storeFeeds(Transaction txn, List<Feed> feeds)
			throws DbException {

		BdfList feedList = new BdfList();
		for (Feed feed : feeds) {
			feedList.add(feedFactory.feedToBdfDictionary(feed));
		}
		BdfDictionary gm = BdfDictionary.of(new BdfEntry(KEY_FEEDS, feedList));
		try {
			clientHelper.mergeGroupMetadata(txn, getLocalGroup().getId(), gm);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	/**
	 * Updates the given feeds in the stored list of feeds, without affecting
	 * any other feeds in the list or re-adding any of the given feeds that
	 * have been removed from the list.
	 */
	private void updateFeeds(List<Feed> updatedFeeds) throws DbException {
		Map<GroupId, Feed> updatedMap = new HashMap<>();
		for (Feed feed : updatedFeeds) updatedMap.put(feed.getBlogId(), feed);
		db.transaction(false, txn -> {
			List<Feed> feeds = getFeeds(txn);
			ListIterator<Feed> it = feeds.listIterator();
			while (it.hasNext()) {
				Feed updated = updatedMap.get(it.next().getBlogId());
				if (updated != null) it.set(updated);
			}
			storeFeeds(txn, feeds);
		});
	}

	/**
	 * This method is called periodically by the task scheduler.
	 * It fetches all available feeds and posts new entries to the respective
	 * blog.
	 * <p>
	 * We can not do this within one database {@link Transaction},
	 * because fetching can take a long time
	 * and we can not block the database that long.
	 */
	@Wakeful
	void fetchFeeds() {
		if (!torActive) return;
		LOG.info("Updating RSS feeds...");

		// Get current feeds
		List<Feed> feeds;
		try {
			feeds = getFeeds();
		} catch (DbException e) {
			logException(LOG, WARNING, e);
			return;
		}

		if (feeds.isEmpty()) {
			LOG.info("No RSS feeds to update");
			return;
		}

		// Fetch and update all feeds
		List<Feed> updatedFeeds = new ArrayList<>(feeds.size());
		for (Feed feed : feeds) {
			try {
				String url = feed.getProperties().getUrl();
				if (url == null) continue;
				// fetch and clean feed
				SyndFeed sf = fetchAndCleanFeed(url);
				// sort and add new entries
				long lastEntryTime = postFeedEntries(feed, sf.getEntries());
				updatedFeeds.add(
						feedFactory.updateFeed(feed, sf, lastEntryTime));
			} catch (IOException | DbException e) {
				logException(LOG, WARNING, e);
			}
		}

		// Store updated feeds
		try {
			updateFeeds(updatedFeeds);
		} catch (DbException e) {
			logException(LOG, WARNING, e);
		}
		LOG.info("Done updating RSS feeds");
	}

	private SyndFeed fetchAndCleanFeed(String url) throws IOException {
		return fetchAndCleanFeed(getFeedInputStream(url));
	}

	private SyndFeed fetchAndCleanFeed(InputStream in) throws IOException {
		SyndFeed sf;
		try {
			sf = getSyndFeed(in);
		} finally {
			tryToClose(in, LOG, WARNING);
		}

		// clean title
		String title = sf.getTitle();
		if (title != null) title = cleanAll(title);
		sf.setTitle(isNullOrEmpty(title) ? "RSS" : title);

		// clean description
		String description = sf.getDescription();
		if (description != null) description = cleanAll(description);
		sf.setDescription(isNullOrEmpty(description) ? null : description);

		// clean author
		String author = sf.getAuthor();
		if (author != null) author = cleanAll(author);
		sf.setAuthor(isNullOrEmpty(author) ? null : author);

		// set other relevant fields to null if empty
		if ("".equals(sf.getLink())) sf.setLink(null);
		if ("".equals(sf.getUri())) sf.setUri(null);

		return sf;
	}

	private InputStream getFeedInputStream(String url) throws IOException {
		// Build Request
		Request request = new Request.Builder()
				.url(url)
				.build();

		// Execute Request
		OkHttpClient client = httpClientProvider.get();
		Response response = client.newCall(request).execute();
		ResponseBody body = response.body();
		if (body != null) return body.byteStream();
		throw new IOException("Empty response body");
	}

	private SyndFeed getSyndFeed(InputStream stream) throws IOException {

		SyndFeedInput input = new SyndFeedInput();
		try {
			return input.build(new XmlReader(stream));
		} catch (IllegalArgumentException | FeedException e) {
			throw new IOException(e);
		}
	}

	private long postFeedEntries(Feed feed, List<SyndEntry> entries)
			throws DbException {

		return db.transactionWithResult(false, txn -> {
			long lastEntryTime = feed.getLastEntryTime();
			//noinspection Java8ListSort
			sort(entries, getEntryComparator());
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
			return lastEntryTime;
		});
	}

	private void postEntry(Transaction txn, Feed feed, SyndEntry entry) {
		LOG.info("Adding new entry...");

		// build post text
		StringBuilder b = new StringBuilder();

		if (!isNullOrEmpty(entry.getTitle())) {
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
		if (!isNullOrEmpty(entry.getAuthor())) {
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
		if (!isNullOrEmpty(link)) {
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
		String text = getPostText(b.toString());
		//noinspection TryWithIdenticalCatches
		try {
			// create and store post
			LocalAuthor localAuthor = feed.getLocalAuthor();
			BlogPost post = blogPostFactory
					.createBlogPost(groupId, time, null, localAuthor, text);
			blogManager.addLocalPost(txn, post);
		} catch (DbException | GeneralSecurityException | FormatException e) {
			logException(LOG, WARNING, e);
		} catch (IllegalArgumentException e) {
			// yes even catch this, so we at least get a stacktrace
			// and the executor doesn't just die a silent death
			logException(LOG, WARNING, e);
		}
	}

	private String getPostText(String text) {
		text = cleanArticle(text);
		return truncateUtf8(text, MAX_BLOG_POST_TEXT_LENGTH);
	}

	private Comparator<SyndEntry> getEntryComparator() {
		return (e1, e2) -> {
			Date d1 = e1.getPublishedDate() != null ? e1.getPublishedDate() :
					e1.getUpdatedDate();
			Date d2 = e2.getPublishedDate() != null ? e2.getPublishedDate() :
					e2.getUpdatedDate();
			if (d1 == null && d2 == null) return 0;
			if (d1 == null) return -1;
			if (d2 == null) return 1;

			if (d1.after(d2)) return 1;
			if (d1.before(d2)) return -1;
			return 0;
		};
	}

	private Group getLocalGroup() {
		return contactGroupFactory.createLocalGroup(CLIENT_ID, MAJOR_VERSION);
	}

}
