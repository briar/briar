package net.sf.briar.util;

public class StringUtils {

	/**
	 * Trims the given string to the given length, returning the head and
	 * appending "..." if the string was trimmed.
	 */
	public static String head(String s, int length) {
		if(s.length() > length) return s.substring(0, length) + "...";
		else return s;
	}

	/**
	 * Trims the given string to the given length, returning the tail and
	 * prepending "..." if the string was trimmed.
	 */
	public static String tail(String s, int length) {
		if(s.length() > length) return "..." + s.substring(s.length() - length);
		else return s;
	}
}
