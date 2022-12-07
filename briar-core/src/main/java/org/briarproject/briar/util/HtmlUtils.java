package org.briarproject.briar.util;

import org.briarproject.nullsafety.NotNullByDefault;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

@NotNullByDefault
public class HtmlUtils {

	private static final Safelist STRIP_ALL = Safelist.none();
	private static final Safelist ARTICLE = Safelist.basic()
			.addTags("h1", "h2", "h3", "h4", "h5", "h6");

	public static String cleanAll(String s) {
		return Jsoup.clean(s, STRIP_ALL);
	}

	public static String cleanArticle(String s) {
		return Jsoup.clean(s, ARTICLE);
	}
}
