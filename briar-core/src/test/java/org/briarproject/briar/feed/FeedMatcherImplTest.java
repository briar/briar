package org.briarproject.briar.feed;

import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.feed.Feed;
import org.briarproject.briar.api.feed.RssProperties;
import org.junit.Test;

import java.util.Random;

import static java.util.Arrays.asList;
import static org.briarproject.bramble.test.TestUtils.getClientId;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class FeedMatcherImplTest extends BrambleTestCase {

	private static final String URL = "url";
	private static final String TITLE = "title";
	private static final String DESCRIPTION = "description";
	private static final String AUTHOR = "author";
	private static final String LINK = "link";
	private static final String URI = "uri";

	private final Random random = new Random();
	private final ClientId clientId = getClientId();
	private final LocalAuthor localAuthor = getLocalAuthor();
	private final FeedMatcher matcher = new FeedMatcherImpl();

	@Test
	public void testFeedWithMatchingUrlIsChosen() {
		RssProperties candidate = new RssProperties(URL,
				TITLE, DESCRIPTION, AUTHOR, LINK, URI);
		// The first feed has a different/null URL but matching RSS fields
		Feed feed1 = createFeed(new RssProperties(nope(),
				TITLE, DESCRIPTION, AUTHOR, LINK, URI));
		// The second feed has a matching URL but different/null RSS fields
		Feed feed2 = createFeed(new RssProperties(URL,
				nope(), nope(), nope(), nope(), nope()));

		Feed match = matcher.findMatchingFeed(candidate, asList(feed1, feed2));

		// The matcher should choose the feed with the matching URL
		assertNotNull(match);
		assertSame(feed2, match);
	}

	@Test
	public void testNullUrlIsNotMatched() {
		// The candidate has a null URL
		RssProperties candidate = new RssProperties(null,
				TITLE, DESCRIPTION, AUTHOR, LINK, URI);
		// The first feed has a non-null URL and matching RSS fields
		Feed feed1 = createFeed(new RssProperties(URL,
				TITLE, DESCRIPTION, AUTHOR, LINK, URI));
		// The second feed has a null URL and different/null RSS fields
		Feed feed2 = createFeed(new RssProperties(null,
				nope(), nope(), nope(), nope(), nope()));

		Feed match = matcher.findMatchingFeed(candidate, asList(feed1, feed2));

		// The matcher should choose the feed with the matching RSS fields
		assertNotNull(match);
		assertSame(feed1, match);
	}

	@Test
	public void testDoesNotMatchOneRssField() {
		testDoesNotMatchRssFields(TITLE, nope(), nope(), nope(), nope());
		testDoesNotMatchRssFields(nope(), DESCRIPTION, nope(), nope(), nope());
		testDoesNotMatchRssFields(nope(), nope(), AUTHOR, nope(), nope());
		testDoesNotMatchRssFields(nope(), nope(), nope(), LINK, nope());
		testDoesNotMatchRssFields(nope(), nope(), nope(), nope(), URL);
	}

	private void testDoesNotMatchRssFields(String title, String description,
			String author, String link, String uri) {
		RssProperties candidate = new RssProperties(null,
				TITLE, DESCRIPTION, AUTHOR, LINK, URL);
		// The first feed has no matching RSS fields
		Feed feed1 = createFeed(new RssProperties(null,
				nope(), nope(), nope(), nope(), nope()));
		// The second feed has the given RSS fields
		Feed feed2 = createFeed(new RssProperties(null,
				title, description, author, link, uri));

		Feed match = matcher.findMatchingFeed(candidate, asList(feed1, feed2));

		// The matcher should not choose either of the feeds
		assertNull(match);
	}

	@Test
	public void testMatchesTwoRssFields() {
		testMatchesRssFields(TITLE, DESCRIPTION, nope(), nope(), nope());
		testMatchesRssFields(nope(), DESCRIPTION, AUTHOR, nope(), nope());
		testMatchesRssFields(nope(), nope(), AUTHOR, LINK, nope());
		testMatchesRssFields(nope(), nope(), nope(), LINK, URI);
	}

	private void testMatchesRssFields(String title, String description,
			String author, String link, String uri) {
		RssProperties candidate = new RssProperties(null,
				TITLE, DESCRIPTION, AUTHOR, LINK, URI);
		// The first feed has no matching RSS fields
		Feed feed1 = createFeed(new RssProperties(null,
				nope(), nope(), nope(), nope(), nope()));
		// The second feed has the given RSS fields
		Feed feed2 = createFeed(new RssProperties(null,
				title, description, author, link, uri));
		// The third feed has one matching RSS field
		Feed feed3 = createFeed(new RssProperties(null,
				TITLE, nope(), nope(), nope(), nope()));

		FeedMatcher matcher = new FeedMatcherImpl();
		Feed match = matcher.findMatchingFeed(candidate,
				asList(feed1, feed2, feed3));

		// The matcher should choose the second feed
		assertSame(feed2, match);
	}

	@Test
	public void testFeedWithMostMatchingRssFieldsIsChosen() {
		RssProperties candidate = new RssProperties(null,
				TITLE, DESCRIPTION, AUTHOR, LINK, URI);
		// The first feed has no matching RSS fields
		Feed feed1 = createFeed(new RssProperties(null,
				nope(), nope(), nope(), nope(), nope()));
		// The second feed has three matching RSS fields
		Feed feed2 = createFeed(new RssProperties(null,
				TITLE, DESCRIPTION, AUTHOR, nope(), nope()));
		// The third feed has two matching RSS fields
		Feed feed3 = createFeed(new RssProperties(null,
				TITLE, DESCRIPTION, nope(), nope(), nope()));

		Feed match = matcher.findMatchingFeed(candidate,
				asList(feed1, feed2, feed3));

		// The matcher should choose the second feed
		assertSame(feed2, match);
	}

	@Test
	public void testNullRssFieldsAreNotMatched() {
		// The candidate has a null URL and null RSS fields
		RssProperties candidate = new RssProperties(null,
				null, null, null, null, null);
		// The first feed has a null URL and non-null RSS fields
		Feed feed1 = createFeed(new RssProperties(null,
				TITLE, DESCRIPTION, AUTHOR, LINK, URI));
		// The second feed has a non-null URL and null RSS fields
		Feed feed2 = createFeed(new RssProperties(URL,
				null, null, null, null, null));

		Feed match = matcher.findMatchingFeed(candidate, asList(feed1, feed2));

		// The matcher should not choose either of the feeds
		assertNull(match);
	}

	/**
	 * Returns an RSS field that doesn't match the default, either because it's
	 * null or because it's a different non-null value.
	 */
	private String nope() {
		return random.nextBoolean() ? null : "x";
	}

	private Feed createFeed(RssProperties properties) {
		Blog blog = new Blog(getGroup(clientId, 123), localAuthor, true);
		return new Feed(blog, localAuthor, properties, 0, 0, 0);
	}
}
