package org.briarproject.bramble.util;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.nullsafety.NotNullByDefault;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Collection;
import java.util.Random;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import static java.nio.charset.CodingErrorAction.IGNORE;
import static java.nio.charset.CodingErrorAction.REPORT;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

@SuppressWarnings("CharsetObjectCanBeUsed")
@NotNullByDefault
public class StringUtils {

	public static final Charset UTF_8 = Charset.forName("UTF-8");
	public static final Charset US_ASCII = Charset.forName("US-ASCII");
	public static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

	private static final Pattern MAC =
			Pattern.compile("[0-9a-f]{2}:[0-9a-f]{2}:" +
							"[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}",
					CASE_INSENSITIVE);

	private static final char[] HEX = new char[] {
			'0', '1', '2', '3', '4', '5', '6', '7',
			'8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
	};
	private static final Random random = new Random();

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
		return s.getBytes(UTF_8);
	}

	public static String fromUtf8(byte[] bytes) throws FormatException {
		return fromUtf8(bytes, 0, bytes.length, true);
	}

	public static String fromUtf8(byte[] bytes, int off, int len)
			throws FormatException {
		return fromUtf8(bytes, off, len, true);
	}

	private static String fromUtf8(byte[] bytes, int off, int len,
			boolean strict) throws FormatException {
		CharsetDecoder decoder = UTF_8.newDecoder();
		decoder.onMalformedInput(strict ? REPORT : IGNORE);
		decoder.onUnmappableCharacter(strict ? REPORT : IGNORE);
		ByteBuffer buffer = ByteBuffer.wrap(bytes, off, len);
		try {
			return decoder.decode(buffer).toString();
		} catch (CharacterCodingException e) {
			throw new FormatException();
		}
	}

	public static String truncateUtf8(String s, int maxUtf8Length) {
		byte[] utf8 = toUtf8(s);
		if (utf8.length <= maxUtf8Length) return s;
		// Don't be strict when converting back, so that if we truncate a
		// multi-byte character the whole character gets dropped
		try {
			return fromUtf8(utf8, 0, maxUtf8Length, false);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
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
	public static byte[] fromHexString(String hex) throws FormatException {
		int len = hex.length();
		if (len % 2 != 0)
			throw new FormatException();
		byte[] bytes = new byte[len / 2];
		for (int i = 0, j = 0; i < len; i += 2, j++) {
			int high = hexDigitToInt(hex.charAt(i));
			int low = hexDigitToInt(hex.charAt(i + 1));
			bytes[j] = (byte) ((high << 4) + low);
		}
		return bytes;
	}

	private static int hexDigitToInt(char c) throws FormatException {
		if (c >= '0' && c <= '9') return c - '0';
		if (c >= 'A' && c <= 'F') return c - 'A' + 10;
		if (c >= 'a' && c <= 'f') return c - 'a' + 10;
		throw new FormatException();
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

	public static boolean isValidMac(String mac) {
		return MAC.matcher(mac).matches();
	}

	public static byte[] macToBytes(String mac) throws FormatException {
		if (!MAC.matcher(mac).matches()) throw new FormatException();
		return fromHexString(mac.replaceAll(":", ""));
	}

	public static String macToString(byte[] mac) throws FormatException {
		if (mac.length != 6) throw new FormatException();
		StringBuilder s = new StringBuilder();
		for (byte b : mac) {
			if (s.length() > 0) s.append(':');
			s.append(HEX[(b >> 4) & 0xF]);
			s.append(HEX[b & 0xF]);
		}
		return s.toString();
	}

	public static String getRandomString(int length) {
		char[] c = new char[length];
		for (int i = 0; i < length; i++)
			c[i] = (char) ('a' + random.nextInt(26));
		return new String(c);
	}

	public static String getRandomBase32String(int length) {
		char[] c = new char[length];
		for (int i = 0; i < length; i++) {
			int character = random.nextInt(32);
			if (character < 26) c[i] = (char) ('a' + character);
			else c[i] = (char) ('2' + (character - 26));
		}
		return new String(c);
	}

	// see https://stackoverflow.com/a/38947571
	static boolean startsWithIgnoreCase(String s, String prefix) {
		return s.regionMatches(true, 0, prefix, 0, prefix.length());
	}
}
