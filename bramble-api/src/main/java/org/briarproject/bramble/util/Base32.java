package org.briarproject.bramble.util;

import java.io.ByteArrayOutputStream;

public class Base32 {

	private static final char[] DIGITS = {
			'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L',
			'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
			'Y', 'Z', '2', '3', '4', '5', '6', '7'
	};

	public static String encode(byte[] b) {
		StringBuilder s = new StringBuilder();
		int byteIndex = 0, currentCode = 0x00;
		int byteMask = 0x80, codeMask = 0x10;
		while (byteIndex < b.length) {
			if ((b[byteIndex] & byteMask) != 0) currentCode |= codeMask;
			// After every 8 bits, move on to the next byte
			if (byteMask == 0x01) {
				byteMask = 0x80;
				byteIndex++;
			} else {
				byteMask >>>= 1;
			}
			// After every 5 bits, move on to the next digit
			if (codeMask == 0x01) {
				s.append(DIGITS[currentCode]);
				codeMask = 0x10;
				currentCode = 0x00;
			} else {
				codeMask >>>= 1;
			}
		}
		// If we're part-way through a digit, output it
		if (codeMask != 0x10) s.append(DIGITS[currentCode]);
		return s.toString();
	}

	public static byte[] decode(String s, boolean strict) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		int digitIndex = 0, digitCount = s.length(), currentByte = 0x00;
		int byteMask = 0x80, codeMask = 0x10;
		while (digitIndex < digitCount) {
			int code = decodeDigit(s.charAt(digitIndex));
			if ((code & codeMask) != 0) currentByte |= byteMask;
			// After every 8 bits, move on to the next byte
			if (byteMask == 0x01) {
				b.write(currentByte);
				byteMask = 0x80;
				currentByte = 0x00;
			} else {
				byteMask >>>= 1;
			}
			// After every 5 bits, move on to the next digit
			if (codeMask == 0x01) {
				codeMask = 0x10;
				digitIndex++;
			} else {
				codeMask >>>= 1;
			}
		}
		// If any extra bits were used for encoding, they should all be zero
		if (strict && byteMask != 0x80 && currentByte != 0x00)
			throw new IllegalArgumentException();
		return b.toByteArray();
	}

	private static int decodeDigit(char c) {
		if (c >= 'A' && c <= 'Z') return c - 'A';
		if (c >= 'a' && c <= 'z') return c - 'a';
		if (c >= '2' && c <= '7') return c - '2' + 26;
		throw new IllegalArgumentException("Not a base32 digit: " + c);
	}
}
