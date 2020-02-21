package org.briarproject.bramble.test;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.util.List;

import javax.annotation.Nullable;

@NotNullByDefault
public class ListMatcher<T> extends BaseMatcher<List<T>> {

	private final Matcher<T> elementMatcher;

	public ListMatcher(Matcher<T> elementMatcher) {
		this.elementMatcher = elementMatcher;
	}

	@Override
	public boolean matches(@Nullable Object item) {
		if (!(item instanceof List)) return false;
		List list = (List) item;
		for (Object element : list) {
			if (!elementMatcher.matches(element)) return false;
		}
		return true;
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("matches a collection");
	}

	public static <T> ListMatcher<T> listOf(Matcher<T> t) {
		return new ListMatcher<>(t);
	}
}
