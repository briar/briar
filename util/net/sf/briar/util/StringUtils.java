package net.sf.briar.util;

public class StringUtils {

	private static final char[] HEX = new char[] {
		'0', '1', '2', '3', '4', '5', '6', '7',
		'8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
	};

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

	/** Converts the given raw byte array to a hex string. */
	public static String toHexString(byte[] bytes) {
		StringBuilder s = new StringBuilder(bytes.length * 2);
		for(byte b : bytes) {
			int high = (b >> 4) & 0xF;
			s.append(HEX[high]);
			int low = b & 0xF;
			s.append(HEX[low]);
		}
		return s.toString();
	}

	/** Converts the given hex string to a raw byte array. */
	public static byte[] fromHexString(String hex) {
		int len = hex.length();
		if(len % 2 != 0) throw new IllegalArgumentException("Not a hex string");
		byte[] bytes = new byte[len / 2];
		for(int i = 0, j = 0; i < len; i += 2, j++) {
			int high = hexDigitToInt(hex.charAt(i));
			int low = hexDigitToInt(hex.charAt(i + 1));
			int b = (high << 4) + low;
			if(b > 127) b -= 256;
			bytes[j] = (byte) b;
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
