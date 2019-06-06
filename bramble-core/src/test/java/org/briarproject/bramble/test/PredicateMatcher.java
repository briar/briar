package org.briarproject.bramble.test;

import org.briarproject.bramble.api.Predicate;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

public class PredicateMatcher<T> extends BaseMatcher<T> {

	private final Class<T> matchedClass;
	private final Predicate<T> predicate;

	public PredicateMatcher(Class<T> matchedClass, Predicate<T> predicate) {
		this.matchedClass = matchedClass;
		this.predicate = predicate;
	}

	@Override
	public boolean matches(Object item) {
		if (matchedClass.isInstance(item))
			return predicate.test(matchedClass.cast(item));
		return false;
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("matches an item against a predicate");
	}
}
