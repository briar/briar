package org.briarproject.briar.util;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

@NotNullByDefault
public class HtmlUtils {

	public static Whitelist STRIP_ALL = Whitelist.none();
	public static Whitelist ARTICLE =
			Whitelist.basic().addTags("h1", "h2", "h3", "h4", "h5", "h6");

	public static String clean(String s, Whitelist list) {
		return Jsoup.clean(s, list);
	}

}
