package org.briarproject.bramble.util;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class StringUtilsTest extends BrambleTestCase {

	@Test
	public void testToHexString() {
		byte[] b = new byte[] {
				0x00, 0x01, 0x02, 0x03, 0x7F, (byte) 0x80,
				0x0A, 0x0B, 0x0C, 0x0D, 0x0E, (byte) 0xFF
		};
		String expected = "000102037F800A0B0C0D0EFF";
		assertEquals(expected, StringUtils.toHexString(b));
	}

	@Test
	public void testToHexStringEmptyInput() {
		assertEquals("", StringUtils.toHexString(new byte[0]));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testFromHexStringRejectsInvalidLength() {
		StringUtils.fromHexString("12345");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testFromHexStringRejectsInvalidCharacter() {
		StringUtils.fromHexString("ABCDEFGH");
	}

	@Test
	public void testFromHexStringUppercase() {
		String s = "000102037F800A0B0C0D0EFF";
		byte[] expected = new byte[] {
				0x00, 0x01, 0x02, 0x03, 0x7F, (byte) 0x80,
				0x0A, 0x0B, 0x0C, 0x0D, 0x0E, (byte) 0xFF
		};
		assertArrayEquals(expected, StringUtils.fromHexString(s));
	}

	@Test
	public void testFromHexStringLowercase() {
		String s = "000102037f800a0b0c0d0eff";
		byte[] expected = new byte[] {
				0x00, 0x01, 0x02, 0x03, 0x7F, (byte) 0x80,
				0x0A, 0x0B, 0x0C, 0x0D, 0x0E, (byte) 0xFF
		};
		assertArrayEquals(expected, StringUtils.fromHexString(s));
	}

	@Test
	public void testFromHexStringEmptyInput() {
		assertArrayEquals(new byte[0], StringUtils.fromHexString(""));
	}

	@Test
	public void testToUtf8EncodesNullCharacterAsStandardUtf8() {
		// The Unicode null character should be encoded as a single null byte,
		// not as two bytes as in CESU-8 and modified UTF-8
		String s = "\u0000";
		assertArrayEquals(new byte[1], StringUtils.toUtf8(s));
	}

	@Test
	public void testToUtf8EncodesSupplementaryCharactersAsStandardUtf8() {
		// A supplementary character should be encoded as four bytes, not as a
		// surrogate pair as in CESU-8 and modified UTF-8
		String s = "\u0045\u0205\uD801\uDC00";
		byte[] expected = new byte[] {
				0x45, // U+0045
				(byte) 0xC8, (byte) 0x85, // U+0205
				(byte) 0xF0, (byte) 0x90, (byte) 0x90, (byte) 0x80 // U+10400
		};
		assertArrayEquals(expected, StringUtils.toUtf8(s));
	}

	@Test
	public void testToUtf8EmptyInput() {
		assertArrayEquals(new byte[0], StringUtils.toUtf8(""));
	}

	@Test
	public void testFromUtf8AcceptsNullCharacterUsingStandardUtf8() {
		// The UTF-8 encoding of the null character is valid
		assertEquals("\u0000", StringUtils.fromUtf8(new byte[1]));
	}

	@Test
	public void testFromUtf8RemovesNullCharacterUsingModifiedUtf8() {
		// The modified UTF-8 encoding of the null character is not valid
		byte[] b = new byte[] {
				(byte) 0xC0, (byte) 0x80, // Null character as modified UTF-8
				(byte) 0xC8, (byte) 0x85 // U+0205
		};
		// Conversion should ignore the invalid character and return the rest
		String expected = "\u0205";
		assertEquals(expected, StringUtils.fromUtf8(b));
	}

	@Test
	public void testFromUtf8AcceptsSupplementaryCharacterUsingStandardUtf8() {
		// The UTF-8 encoding of a supplementary character is valid and should
		// be converted to a surrogate pair
		byte[] b = new byte[] {
				(byte) 0xF0, (byte) 0x90, (byte) 0x90, (byte) 0x80, // U+10400
				(byte) 0xC8, (byte) 0x85 // U+0205
		};
		String expected = "\uD801\uDC00\u0205"; // Surrogate pair
		assertEquals(expected, StringUtils.fromUtf8(b));
	}

	@Test
	public void testFromUtf8RemovesSupplementaryCharacterUsingModifiedUtf8() {
		// The CESU-8 or modified UTF-8 encoding of a supplementary character
		// is not valid
		byte[] b = new byte[] {
				(byte) 0xED, (byte) 0xA0, (byte) 0x81, // U+10400 as CSEU-8
				(byte) 0xED, (byte) 0xB0, (byte) 0x80,
				(byte) 0xC8, (byte) 0x85 // U+0205
		};
		// Conversion should ignore the invalid character and return the rest
		String expected = "\u0205";
		assertEquals(expected, StringUtils.fromUtf8(b));
	}

	@Test
	public void testFromUtf8EmptyInput() {
		assertEquals("", StringUtils.fromUtf8(new byte[0]));
	}

	@Test
	public void testTruncateUtf8ReturnsArgumentIfNotTruncated() {
		String s = "Hello";
		assertSame(s, StringUtils.truncateUtf8(s, 5));
	}

	@Test
	public void testTruncateUtf8ChecksUtf8LengthNotStringLength() {
		String s = "H\u0205llo";
		assertEquals(5, s.length());
		assertEquals(6, StringUtils.toUtf8(s).length);
		String expected = "H\u0205ll"; // Sixth byte removed
		assertEquals(expected, StringUtils.truncateUtf8(s, 5));
	}

	@Test
	public void testTruncateUtf8RemovesTruncatedCharacter() {
		String s = "\u0205\u0205"; // String requires four bytes
		String expected = "\u0205"; // Partial character removed
		String truncated = StringUtils.truncateUtf8(s, 3);
		assertEquals(expected, truncated);
		// Converting the truncated string should not exceed the max length
		assertEquals(2, StringUtils.toUtf8(truncated).length);
	}

	@Test
	public void testTruncateUtf8RemovesTruncatedSurrogatePair() {
		String s = "\u0205\uD801\uDC00"; // String requires six bytes
		String expected = "\u0205"; // Partial character removed
		String truncated = StringUtils.truncateUtf8(s, 3);
		assertEquals(expected, truncated);
		// Converting the truncated string should not exceed the max length
		assertEquals(2, StringUtils.toUtf8(truncated).length);
	}

	@Test
	public void testTruncateUtf8EmptyInput() {
		assertEquals("", StringUtils.truncateUtf8("", 123));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMacToBytesRejectsShortMac() {
		StringUtils.macToBytes("00:00:00:00:00");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMacToBytesRejectsLongMac() {
		StringUtils.macToBytes("00:00:00:00:00:00:00");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMacToBytesRejectsInvalidCharacter() {
		StringUtils.macToBytes("00:00:00:00:00:0g");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMacToBytesRejectsInvalidFormat() {
		StringUtils.macToBytes("0:000:00:00:00:00");
	}

	@Test
	public void testMacToBytesUpperCase() {
		byte[] expected = new byte[] {0x0A, 0x1B, 0x2C, 0x3D, 0x4E, 0x5F};
		String mac = "0A:1B:2C:3D:4E:5F";
		assertArrayEquals(expected, StringUtils.macToBytes(mac));
	}

	@Test
	public void testMacToBytesLowerCase() {
		byte[] expected = new byte[] {0x0A, 0x1B, 0x2C, 0x3D, 0x4E, 0x5F};
		String mac = "0a:1b:2c:3d:4e:5f";
		assertArrayEquals(expected, StringUtils.macToBytes(mac));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMacToStringRejectsShortMac() {
		StringUtils.macToString(new byte[5]);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMacToStringRejectsLongMac() {
		StringUtils.macToString(new byte[7]);
	}

	@Test
	public void testMacToString() {
		byte[] mac = new byte[] {0x0a, 0x1b, 0x2c, 0x3d, 0x4e, 0x5f};
		String expected = "0A:1B:2C:3D:4E:5F";
		assertEquals(expected, StringUtils.macToString(mac));
	}
}
