package org.briarproject.util;

import java.nio.charset.Charset;

public class StringUtils {

	private static final char[] HEX = new char[] {
		'0', '1', '2', '3', '4', '5', '6', '7',
		'8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
	};

	public static boolean isNullOrEmpty(String s) {
		return s == null || s.length() == 0;
	}

	public static byte[] toUtf8(String s) {
		return s.getBytes(Charset.forName("UTF-8"));
	}

	public static String fromUtf8(byte[] bytes) {
		return new String(bytes, Charset.forName("UTF-8"));
	}

	/** Converts the given byte array to a hex character array. */
	public static char[] toHexChars(byte[] bytes) {
		char[] hex = new char[bytes.length * 2];
		for(int i = 0, j = 0; i < bytes.length; i++) {
			hex[j++] = HEX[(bytes[i] >> 4) & 0xF];
			hex[j++] = HEX[bytes[i] & 0xF];
		}
		return hex;
	}

	/** Converts the given byte array to a hex string. */
	public static String toHexString(byte[] bytes) {
		return new String(toHexChars(bytes));
	}

	/** Converts the given hex string to a byte array. */
	public static byte[] fromHexString(String hex) {
		int len = hex.length();
		if(len % 2 != 0) throw new IllegalArgumentException("Not a hex string");
		byte[] bytes = new byte[len / 2];
		for(int i = 0, j = 0; i < len; i += 2, j++) {
			int high = hexDigitToInt(hex.charAt(i));
			int low = hexDigitToInt(hex.charAt(i + 1));
			bytes[j] = (byte) ((high << 4) + low);
		}
		return bytes;
	}

	private static int hexDigitToInt(char c) {
		if(c >= '0' && c <= '9') return c - '0';
		if(c >= 'A' && c <= 'F') return c - 'A' + 10;
		if(c >= 'a' && c <= 'f') return c - 'a' + 10;
		throw new IllegalArgumentException("Not a hex digit: " + c);
	}
}
