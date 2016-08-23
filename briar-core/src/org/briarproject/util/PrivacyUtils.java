package org.briarproject.util;

public class PrivacyUtils {

	public static String scrubOnion(String onion) {
		return onion.substring(0, 3) + "[_scrubbed_]";
	}

}
