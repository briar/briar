package org.briarproject.bramble.test;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import javax.annotation.Nullable;

@NotNullByDefault
public class PairMatcher<A, B> extends BaseMatcher<Pair<A, B>> {

	private final Matcher<A> firstMatcher;
	private final Matcher<B> secondMatcher;

	public PairMatcher(Matcher<A> firstMatcher, Matcher<B> secondMatcher) {
		this.firstMatcher = firstMatcher;
		this.secondMatcher = secondMatcher;
	}

	@Override
	public boolean matches(@Nullable Object item) {
		if (!(item instanceof Pair)) return false;
		Pair pair = (Pair) item;
		return firstMatcher.matches(pair.getFirst()) &&
				secondMatcher.matches(pair.getSecond());
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("matches a pair");
	}

	public static <A, B> PairMatcher<A, B> pairOf(Matcher<A> a, Matcher<B> b) {
		return new PairMatcher<>(a, b);
	}
}
