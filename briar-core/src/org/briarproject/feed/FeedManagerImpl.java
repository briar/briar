package org.briarproject.feed;

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import org.briarproject.api.FormatException;
import org.briarproject.api.blogs.BlogManager;
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
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.lifecycle.ServiceException;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import javax.inject.Inject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.feed.FeedConstants.FETCH_DELAY_INITIAL;
import static org.briarproject.api.feed.FeedConstants.FETCH_INTERVAL;
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
	private final BlogManager blogManager;

	@Inject
	FeedManagerImpl(ScheduledExecutorService feedExecutor,
			@IoExecutor Executor ioExecutor, DatabaseComponent db,
			PrivateGroupFactory privateGroupFactory, ClientHelper clientHelper,
			BlogManager blogManager) {

		this.feedExecutor = feedExecutor;
		this.ioExecutor = ioExecutor;
		this.db = db;
		this.privateGroupFactory = privateGroupFactory;
		this.clientHelper = clientHelper;
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
				FETCH_INTERVAL, MINUTES);
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
		Feed feed;
		try {
			SyndFeed f = getSyndFeed(getFeedInputStream(url));
			String title = f.getTitle();
			String description = f.getDescription();
			String author = f.getAuthor();
			long added = System.currentTimeMillis();
			feed = new Feed(url, g, title, description, author, added, added);
		} catch (FeedException e) {
			throw new IOException(e);
		}

		Transaction txn = db.startTransaction(false);
		try {
			List<Feed> feeds = getFeeds(txn);
			feeds.add(feed);
			storeFeeds(feeds);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void removeFeed(String url) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			List<Feed> feeds = getFeeds(txn);
			boolean found = false;
			for (Feed feed : feeds) {
				if (feed.getUrl().equals(url)) {
					found = true;
					feeds.remove(feed);
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

	private void fetchFeeds() {
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
			newFeeds.add(fetchFeed(feed));
		}

		// Store updated feeds
		try {
			storeFeeds(newFeeds);
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING))
				LOG.log(WARNING, e.toString(), e);
		}
	}

	private Feed fetchFeed(Feed feed) {
		LOG.info("Updating RSS feeds...");
		String title, description, author;
		long updated = System.currentTimeMillis();
		try {
			SyndFeed f = getSyndFeed(getFeedInputStream(feed.getUrl()));
			title = f.getTitle();
			description = f.getDescription();
			author = f.getAuthor();

			// TODO keep track of which entries have been seen (#485)
			// TODO Pass any new entries down the pipeline to be posted (#486)
		} catch (FeedException e) {
			if (LOG.isLoggable(WARNING))
				LOG.log(WARNING, e.toString(), e);
			return feed;
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING))
				LOG.log(WARNING, e.toString(), e);
			return feed;
		}
		return new Feed(feed.getUrl(), feed.getBlogId(), title, description,
				author, feed.getAdded(), updated);
	}

	private InputStream getFeedInputStream(String url) throws IOException {
		// Set proxy
		// TODO verify and use local Tor proxy address/port
		String proxyHost = "localhost";
		int proxyPort = 59050;
		Proxy proxy = new Proxy(Proxy.Type.HTTP,
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

	private Group getLocalGroup() {
		return privateGroupFactory.createLocalGroup(getClientId());
	}

}
