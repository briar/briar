package org.briarproject.briar.feed;

import com.rometools.rome.feed.synd.SyndFeed;

import org.briarproject.bramble.api.WeakSingletonProvider;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogPost;
import org.briarproject.briar.api.blog.BlogPostFactory;
import org.briarproject.briar.api.feed.Feed;
import org.briarproject.briar.api.feed.RssProperties;
import org.jmock.Expectations;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executor;

import javax.annotation.Nonnull;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static java.util.Collections.singletonList;
import static okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.briarproject.bramble.util.StringUtils.UTF_8;
import static org.briarproject.briar.api.feed.FeedConstants.KEY_FEEDS;
import static org.briarproject.briar.api.feed.FeedManager.CLIENT_ID;
import static org.briarproject.briar.api.feed.FeedManager.MAJOR_VERSION;
import static org.hamcrest.Matchers.nullValue;

public class FeedManagerImplTest extends BrambleMockTestCase {

	private final TaskScheduler scheduler = context.mock(TaskScheduler.class);
	private final Executor ioExecutor = new ImmediateExecutor();
	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final ContactGroupFactory contactGroupFactory =
			context.mock(ContactGroupFactory.class);
	private final ClientHelper clientHelper = context.mock(ClientHelper.class);
	private final BlogManager blogManager = context.mock(BlogManager.class);
	private final BlogPostFactory blogPostFactory =
			context.mock(BlogPostFactory.class);
	private final FeedFactory feedFactory = context.mock(FeedFactory.class);
	private final FeedMatcher feedMatcher = context.mock(FeedMatcher.class);
	private final Clock clock = context.mock(Clock.class);

	private final OkHttpClient client = new OkHttpClient.Builder().build();
	private final WeakSingletonProvider<OkHttpClient> httpClientProvider =
			new WeakSingletonProvider<OkHttpClient>() {
				@Override
				@Nonnull
				public OkHttpClient createInstance() {
					return client;
				}
			};
	private final Group localGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
	private final GroupId localGroupId = localGroup.getId();
	private final Group blogGroup =
			getGroup(BlogManager.CLIENT_ID, BlogManager.MAJOR_VERSION);
	private final GroupId blogGroupId = blogGroup.getId();
	private final LocalAuthor localAuthor = getLocalAuthor();
	private final Blog blog = new Blog(blogGroup, localAuthor, true);
	private final Message message = getMessage(blogGroupId);
	private final BlogPost blogPost = new BlogPost(message, null, localAuthor);

	private final long now = System.currentTimeMillis();
	// Round the publication date to a whole second to avoid rounding errors
	private final long pubDate = now / 1000 * 1000 - 1000;
	private final SimpleDateFormat sdf =
			new SimpleDateFormat("EEE, dd MMM yy HH:mm:ss Z");
	private final String pubDateString = sdf.format(new Date(pubDate));

	private final FeedManagerImpl feedManager =
			new FeedManagerImpl(scheduler, ioExecutor, db, contactGroupFactory,
					clientHelper, blogManager, blogPostFactory, feedFactory,
					feedMatcher, httpClientProvider, clock);

	@Test
	public void testFetchFeedsReturnsEarlyIfTorIsNotActive() {
		feedManager.setTorActive(false);
		feedManager.fetchFeeds();
	}

	@Test
	public void testFetchFeedsEmptyList() throws Exception {
		// The list of feeds is empty
		expectGetFeeds();

		feedManager.setTorActive(true);
		feedManager.fetchFeeds();
	}

	@Test
	public void testFetchFeedsIoException() throws Exception {
		// Fetching the feed will fail
		MockWebServer server = new MockWebServer();
		String url = server.url("/").toString();
		server.enqueue(new MockResponse()
				.setBody("  ")
				.setSocketPolicy(DISCONNECT_DURING_RESPONSE_BODY));

		Feed feed = createFeed(url, blog);

		expectGetFeeds(feed);
		expectGetAndStoreFeeds(feed);

		feedManager.setTorActive(true);
		feedManager.fetchFeeds();
	}

	@Test
	public void testFetchFeedsEmptyResponseBody() throws Exception {
		// Fetching the feed will succeed, but parsing the empty body will fail
		MockWebServer server = new MockWebServer();
		String url = server.url("/").toString();
		server.enqueue(new MockResponse());

		Feed feed = createFeed(url, blog);

		expectGetFeeds(feed);
		expectGetAndStoreFeeds(feed);

		feedManager.setTorActive(true);
		feedManager.fetchFeeds();
	}

	@Test
	public void testFetchFeedsNoEntries() throws Exception {
		// Fetching and parsing the feed will succeed; there are no entries
		String feedXml = createRssFeedXml();

		MockWebServer server = new MockWebServer();
		String url = server.url("/").toString();
		server.enqueue(new MockResponse().setBody(feedXml));

		Feed feed = createFeed(url, blog);

		expectGetFeeds(feed);
		expectUpdateFeedNoEntries(feed);
		expectGetAndStoreFeeds(feed);

		feedManager.setTorActive(true);
		feedManager.fetchFeeds();
	}

	@Test
	public void testFetchFeedsOneEntry() throws Exception {
		// Fetching and parsing the feed will succeed; there is one entry
		String entryXml =
				"<item><pubDate>" + pubDateString + "</pubDate></item>";
		String feedXml = createRssFeedXml(entryXml);

		MockWebServer server = new MockWebServer();
		String url = server.url("/").toString();
		server.enqueue(new MockResponse().setBody(feedXml));

		Feed feed = createFeed(url, blog);

		expectGetFeeds(feed);
		expectUpdateFeedOneEntry(feed);
		expectGetAndStoreFeeds(feed);

		feedManager.setTorActive(true);
		feedManager.fetchFeeds();
	}

	@Test
	public void testAddNewFeedFromUrl() throws Exception {
		// Fetching and parsing the feed will succeed; there are no entries
		String feedXml = createRssFeedXml();

		MockWebServer server = new MockWebServer();
		String url = server.url("/").toString();
		server.enqueue(new MockResponse().setBody(feedXml));

		Feed newFeed = createFeed(url, blog);

		Group existingBlogGroup = getGroup(BlogManager.CLIENT_ID,
				BlogManager.MAJOR_VERSION);
		Blog existingBlog = new Blog(existingBlogGroup, localAuthor, true);
		Feed existingFeed = createFeed("http://example.com", existingBlog);

		expectGetFeeds(existingFeed);

		context.checking(new DbExpectations() {{
			// The added feed doesn't match any existing feed
			oneOf(feedMatcher).findMatchingFeed(with(any(RssProperties.class)),
					with(singletonList(existingFeed)));
			will(returnValue(null));
			// Create the new feed
			oneOf(feedFactory).createFeed(with(url), with(any(SyndFeed.class)));
			will(returnValue(newFeed));
			// Add the new feed to the list of feeds
			Transaction txn = new Transaction(null, false);
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(blogManager).addBlog(txn, blog);
			expectGetFeeds(txn, existingFeed);
			expectStoreFeeds(txn, existingFeed, newFeed);
		}});

		expectUpdateFeedNoEntries(newFeed);
		expectGetAndStoreFeeds(existingFeed, newFeed);

		feedManager.addFeed(url);
	}

	@Test
	public void testAddExistingFeedFromUrl() throws Exception {
		// Fetching and parsing the feed will succeed; there are no entries
		String feedXml = createRssFeedXml();

		MockWebServer server = new MockWebServer();
		String url = server.url("/").toString();
		server.enqueue(new MockResponse().setBody(feedXml));

		Feed newFeed = createFeed(url, blog);

		expectGetFeeds(newFeed);

		context.checking(new DbExpectations() {{
			// The added feed matches an existing feed
			oneOf(feedMatcher).findMatchingFeed(with(any(RssProperties.class)),
					with(singletonList(newFeed)));
			will(returnValue(newFeed));
		}});

		expectUpdateFeedNoEntries(newFeed);
		expectGetAndStoreFeeds(newFeed);

		feedManager.addFeed(url);
	}

	@Test
	public void testAddNewFeedFromInputStream() throws Exception {
		// Reading and parsing the feed will succeed; there are no entries
		String feedXml = createRssFeedXml();
		Feed newFeed = createFeed(null, blog);

		Group existingBlogGroup = getGroup(BlogManager.CLIENT_ID,
				BlogManager.MAJOR_VERSION);
		Blog existingBlog = new Blog(existingBlogGroup, localAuthor, true);
		Feed existingFeed = createFeed(null, existingBlog);

		expectGetFeeds(existingFeed);

		context.checking(new DbExpectations() {{
			// The added feed doesn't match any existing feed
			oneOf(feedMatcher).findMatchingFeed(with(any(RssProperties.class)),
					with(singletonList(existingFeed)));
			will(returnValue(null));
			// Create the new feed
			oneOf(feedFactory).createFeed(with(nullValue(String.class)),
					with(any(SyndFeed.class)));
			will(returnValue(newFeed));
			// Add the new feed to the list of feeds
			Transaction txn = new Transaction(null, false);
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(blogManager).addBlog(txn, blog);
			expectGetFeeds(txn, existingFeed);
			expectStoreFeeds(txn, existingFeed, newFeed);
		}});

		expectUpdateFeedNoEntries(newFeed);
		expectGetAndStoreFeeds(existingFeed, newFeed);

		feedManager.addFeed(new ByteArrayInputStream(feedXml.getBytes(UTF_8)));
	}

	@Test
	public void testAddExistingFeedFromInputStream() throws Exception {
		// Reading and parsing the feed will succeed; there are no entries
		String feedXml = createRssFeedXml();
		Feed newFeed = createFeed(null, blog);

		expectGetFeeds(newFeed);

		context.checking(new DbExpectations() {{
			// The added feed matches an existing feed
			oneOf(feedMatcher).findMatchingFeed(with(any(RssProperties.class)),
					with(singletonList(newFeed)));
			will(returnValue(newFeed));
		}});

		expectUpdateFeedNoEntries(newFeed);
		expectGetAndStoreFeeds(newFeed);

		feedManager.addFeed(new ByteArrayInputStream(feedXml.getBytes(UTF_8)));
	}

	private Feed createFeed(String url, Blog blog) {
		RssProperties properties = new RssProperties(url,
				null, null, null, null, null);
		return new Feed(blog, localAuthor, properties, 0, 0, 0);
	}

	private String createRssFeedXml(String... entries) {
		StringBuilder sb = new StringBuilder();
		sb.append("<rss version='2.0'><channel>");
		for (String entry : entries) sb.append(entry);
		sb.append("</channel></rss>");
		return sb.toString();
	}

	private void expectGetLocalGroup() {
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createLocalGroup(CLIENT_ID,
					MAJOR_VERSION);
			will(returnValue(localGroup));
		}});
	}

	private void expectGetFeeds(Feed... feeds) throws Exception {
		Transaction txn = new Transaction(null, true);
		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
		}});
		expectGetFeeds(txn, feeds);
	}

	private void expectGetFeeds(Transaction txn, Feed... feeds)
			throws Exception {
		BdfList feedList = new BdfList();
		for (int i = 0; i < feeds.length; i++) {
			feedList.add(new BdfDictionary());
		}
		BdfDictionary feedsDict =
				BdfDictionary.of(new BdfEntry(KEY_FEEDS, feedList));
		expectGetLocalGroup();

		context.checking(new Expectations() {{
			oneOf(clientHelper).getGroupMetadataAsDictionary(txn, localGroupId);
			will(returnValue(feedsDict));
			for (int i = 0; i < feeds.length; i++) {
				oneOf(feedFactory).createFeed(feedList.getDictionary(i));
				will(returnValue(feeds[i]));
			}
		}});
	}

	private void expectStoreFeeds(Transaction txn, Feed... feeds)
			throws Exception {
		BdfList feedList = new BdfList();
		for (int i = 0; i < feeds.length; i++) {
			feedList.add(new BdfDictionary());
		}
		BdfDictionary feedDict =
				BdfDictionary.of(new BdfEntry(KEY_FEEDS, feedList));
		expectGetLocalGroup();

		context.checking(new Expectations() {{
			oneOf(clientHelper).mergeGroupMetadata(txn, localGroupId, feedDict);
			for (int i = 0; i < feeds.length; i++) {
				oneOf(feedFactory).feedToBdfDictionary(feeds[i]);
				will(returnValue(feedList.getDictionary(i)));
			}
		}});
	}

	private void expectGetAndStoreFeeds(Feed... feeds) throws Exception {
		context.checking(new DbExpectations() {{
			Transaction txn = new Transaction(null, false);
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			expectGetFeeds(txn, feeds);
			expectStoreFeeds(txn, feeds);
		}});
	}

	private void expectUpdateFeedNoEntries(Feed feed) throws Exception {
		Transaction txn = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(false), withDbCallable(txn));
			oneOf(feedFactory).updateFeed(with(feed), with(any(SyndFeed.class)),
					with(0L));
			will(returnValue(feed));
		}});
	}

	private void expectUpdateFeedOneEntry(Feed feed) throws Exception {
		Transaction txn = new Transaction(null, false);
		String body = "<p>(" + new Date(pubDate) + ")</p>";

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(false), withDbCallable(txn));
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(blogPostFactory).createBlogPost(blogGroupId, pubDate, null,
					localAuthor, body);
			will(returnValue(blogPost));
			oneOf(blogManager).addLocalPost(txn, blogPost);
			oneOf(feedFactory).updateFeed(with(feed), with(any(SyndFeed.class)),
					with(pubDate));
			will(returnValue(feed));
		}});
	}
}
