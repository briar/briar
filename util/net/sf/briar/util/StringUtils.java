package net.sf.briar.util;

public class StringUtils {

	public static String head(String s, int length) {
		if(s.length() > length) return s.substring(0, length) + "...";
		else return s;
	}

	public static String tail(String s, int length) {
		if(s.length() > length) return "..." + s.substring(s.length() - length);
		else return s;
	}
}
