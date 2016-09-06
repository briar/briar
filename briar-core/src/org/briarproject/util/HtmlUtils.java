package org.briarproject.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

public class HtmlUtils {

	public static Whitelist stripAll = Whitelist.none();
	public static Whitelist article =
			Whitelist.basic().addTags("h1", "h2", "h3", "h4", "h5", "h6");

	public static String clean(String s, Whitelist list) {
		return Jsoup.clean(s, list);
	}

}
