package org.briarproject.briar.feed;

import org.briarproject.briar.api.feed.Feed;
import org.briarproject.briar.api.feed.RssProperties;
import org.briarproject.nullsafety.NotNullByDefault;

import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;

@NotNullByDefault
class FeedMatcherImpl implements FeedMatcher {

	private static final int MIN_MATCHING_FIELDS = 2;

	@Inject
	FeedMatcherImpl() {
	}

	@Nullable
	@Override
	public Feed findMatchingFeed(RssProperties candidate, List<Feed> feeds) {
		// First pass: if the candidate was imported from a URL and we have
		// a feed that was imported from the same URL then it's a match
		String url = candidate.getUrl();
		if (url != null) {
			for (Feed f : feeds) {
				if (url.equals(f.getProperties().getUrl())) return f;
			}
		}
		// Second pass: if the candidate matches at least MIN_MATCHING_FIELDS
		// out of the title, description, author, link and URI, then return the
		// feed with the highest number of matching fields
		int bestScore = 0;
		Feed bestFeed = null;
		String title = candidate.getTitle();
		String description = candidate.getDescription();
		String author = candidate.getAuthor();
		String link = candidate.getLink();
		String uri = candidate.getUri();
		for (Feed f : feeds) {
			int score = 0;
			RssProperties p = f.getProperties();
			if (title != null && title.equals(p.getTitle())) {
				score++;
			}
			if (description != null && description.equals(p.getDescription())) {
				score++;
			}
			if (author != null && author.equals(p.getAuthor())) {
				score++;
			}
			if (link != null && link.equals(p.getLink())) {
				score++;
			}
			if (uri != null && uri.equals(p.getUri())) {
				score++;
			}
			if (score > bestScore) {
				bestScore = score;
				bestFeed = f;
			}
		}
		if (bestScore >= MIN_MATCHING_FIELDS) return bestFeed;
		// No match
		return null;
	}
}
