package org.briarproject.bramble.util;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Collection;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import static java.nio.charset.CodingErrorAction.IGNORE;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

@NotNullByDefault
public class StringUtils {

	private static final Charset UTF_8 = Charset.forName("UTF-8");
	private static Pattern MAC = Pattern.compile("[0-9a-f]{2}:[0-9a-f]{2}:" +
					"[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}",
			CASE_INSENSITIVE);

	private static final char[] HEX = new char[] {
			'0', '1', '2', '3', '4', '5', '6', '7',
			'8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
	};

	public static boolean isNullOrEmpty(@Nullable String s) {
		return s == null || s.length() == 0;
	}

	public static String join(Collection<String> strings, String separator) {
		StringBuilder joined = new StringBuilder();
		for (String s : strings) {
			if (joined.length() > 0) joined.append(separator);
			joined.append(s);
		}
		return joined.toString();
	}

	public static byte[] toUtf8(String s) {
		try {
			return s.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	public static String fromUtf8(byte[] bytes) {
		return fromUtf8(bytes, 0, bytes.length);
	}

	public static String fromUtf8(byte[] bytes, int off, int len) {
		CharsetDecoder decoder = UTF_8.newDecoder();
		decoder.onMalformedInput(IGNORE);
		decoder.onUnmappableCharacter(IGNORE);
		ByteBuffer buffer = ByteBuffer.wrap(bytes, off, len);
		try {
			return decoder.decode(buffer).toString();
		} catch (CharacterCodingException e) {
			throw new RuntimeException(e);
		}
	}

	public static String truncateUtf8(String s, int maxUtf8Length) {
		byte[] utf8 = toUtf8(s);
		if (utf8.length <= maxUtf8Length) return s;
		return fromUtf8(utf8, 0, maxUtf8Length);
	}

	/**
	 * Converts the given byte array to a hex character array.
	 */
	private static char[] toHexChars(byte[] bytes) {
		char[] hex = new char[bytes.length * 2];
		for (int i = 0, j = 0; i < bytes.length; i++) {
			hex[j++] = HEX[(bytes[i] >> 4) & 0xF];
			hex[j++] = HEX[bytes[i] & 0xF];
		}
		return hex;
	}

	/**
	 * Converts the given byte array to a hex string.
	 */
	public static String toHexString(byte[] bytes) {
		return new String(toHexChars(bytes));
	}

	/**
	 * Converts the given hex string to a byte array.
	 */
	public static byte[] fromHexString(String hex) {
		int len = hex.length();
		if (len % 2 != 0)
			throw new IllegalArgumentException("Not a hex string");
		byte[] bytes = new byte[len / 2];
		for (int i = 0, j = 0; i < len; i += 2, j++) {
			int high = hexDigitToInt(hex.charAt(i));
			int low = hexDigitToInt(hex.charAt(i + 1));
			bytes[j] = (byte) ((high << 4) + low);
		}
		return bytes;
	}

	private static int hexDigitToInt(char c) {
		if (c >= '0' && c <= '9') return c - '0';
		if (c >= 'A' && c <= 'F') return c - 'A' + 10;
		if (c >= 'a' && c <= 'f') return c - 'a' + 10;
		throw new IllegalArgumentException("Not a hex digit: " + c);
	}

	public static String trim(String s) {
		return s.trim();
	}

	/**
	 * Returns true if the string is longer than maxLength
	 */
	public static boolean utf8IsTooLong(String s, int maxLength) {
		return toUtf8(s).length > maxLength;
	}

	public static byte[] macToBytes(String mac) {
		if (!MAC.matcher(mac).matches()) throw new IllegalArgumentException();
		return fromHexString(mac.replaceAll(":", ""));
	}

	public static String macToString(byte[] mac) {
		if (mac.length != 6) throw new IllegalArgumentException();
		StringBuilder s = new StringBuilder();
		for (byte b : mac) {
			if (s.length() > 0) s.append(':');
			s.append(HEX[(b >> 4) & 0xF]);
			s.append(HEX[b & 0xF]);
		}
		return s.toString();
	}
}
