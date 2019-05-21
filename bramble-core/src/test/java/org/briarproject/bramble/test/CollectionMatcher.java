package org.briarproject.bramble.test;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.util.Collection;

import javax.annotation.Nullable;

@NotNullByDefault
public class CollectionMatcher<T> extends BaseMatcher<Collection<T>> {

	private final Matcher<T> elementMatcher;

	public CollectionMatcher(Matcher<T> elementMatcher) {
		this.elementMatcher = elementMatcher;
	}

	@Override
	public boolean matches(@Nullable Object item) {
		if (!(item instanceof Collection)) return false;
		Collection collection = (Collection) item;
		for (Object element : collection) {
			if (!elementMatcher.matches(element)) return false;
		}
		return true;
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("matches a collection");
	}

	public static <T> CollectionMatcher<T> collectionOf(Matcher<T> t) {
		return new CollectionMatcher<>(t);
	}
}
