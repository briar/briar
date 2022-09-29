package org.briarproject.briar.util;

import org.briarproject.nullsafety.NotNullByDefault;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

@NotNullByDefault
public class HtmlUtils {

	public static Safelist STRIP_ALL = Safelist.none();
	public static Safelist ARTICLE = Safelist.basic()
			.addTags("h1", "h2", "h3", "h4", "h5", "h6");

	public static String clean(String s, Safelist list) {
		return Jsoup.clean(s, list);
	}

}
