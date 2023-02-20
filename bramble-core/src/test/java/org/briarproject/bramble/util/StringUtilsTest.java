package org.briarproject.bramble.util;

import org.briarproject.bramble.api.FormatException;
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

	@Test(expected = FormatException.class)
	public void testFromHexStringRejectsInvalidLength() throws FormatException {
		StringUtils.fromHexString("12345");
	}

	@Test(expected = FormatException.class)
	public void testFromHexStringRejectsInvalidCharacter()
			throws FormatException {
		StringUtils.fromHexString("ABCDEFGH");
	}

	@Test
	public void testFromHexStringUppercase() throws FormatException {
		String s = "000102037F800A0B0C0D0EFF";
		byte[] expected = new byte[] {
				0x00, 0x01, 0x02, 0x03, 0x7F, (byte) 0x80,
				0x0A, 0x0B, 0x0C, 0x0D, 0x0E, (byte) 0xFF
		};
		assertArrayEquals(expected, StringUtils.fromHexString(s));
	}

	@Test
	public void testFromHexStringLowercase() throws FormatException {
		String s = "000102037f800a0b0c0d0eff";
		byte[] expected = new byte[] {
				0x00, 0x01, 0x02, 0x03, 0x7F, (byte) 0x80,
				0x0A, 0x0B, 0x0C, 0x0D, 0x0E, (byte) 0xFF
		};
		assertArrayEquals(expected, StringUtils.fromHexString(s));
	}

	@Test
	public void testFromHexStringEmptyInput() throws FormatException {
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
	public void testFromUtf8AcceptsNullCharacterUsingStandardUtf8()
			throws Exception {
		// The UTF-8 encoding of the null character is valid
		byte[] utf8 = new byte[1];
		String actual = StringUtils.fromUtf8(utf8);
		assertEquals("\u0000", actual);
		// When we convert back to UTF-8 we should get the original encoding
		assertArrayEquals(utf8, StringUtils.toUtf8(actual));
	}

	@Test(expected = FormatException.class)
	public void testFromUtf8RejectsNullCharacterUsingModifiedUtf8()
			throws Exception {
		// The modified UTF-8 encoding of the null character is not valid
		byte[] b = new byte[] {
				(byte) 0xC0, (byte) 0x80, // Null character as modified UTF-8
				(byte) 0xC8, (byte) 0x85 // U+0205
		};
		StringUtils.fromUtf8(b);
	}

	@Test
	public void testFromUtf8AcceptsSupplementaryCharacterUsingStandardUtf8()
			throws Exception {
		// The UTF-8 encoding of a supplementary character is valid and should
		// be converted to a surrogate pair
		byte[] utf8 = new byte[] {
				(byte) 0xF0, (byte) 0x90, (byte) 0x90, (byte) 0x80, // U+10400
				(byte) 0xC8, (byte) 0x85 // U+0205
		};
		String expected = "\uD801\uDC00\u0205"; // Surrogate pair
		String actual = StringUtils.fromUtf8(utf8);
		assertEquals(expected, actual);
		// When we convert back to UTF-8 we should get the original encoding
		assertArrayEquals(utf8, StringUtils.toUtf8(actual));
	}

	@Test(expected = FormatException.class)
	public void testFromUtf8RejectsSupplementaryCharacterUsingModifiedUtf8()
			throws Exception {
		// The CESU-8 or modified UTF-8 encoding of a supplementary character
		// is not valid
		byte[] utf8 = new byte[] {
				(byte) 0xED, (byte) 0xA0, (byte) 0x81, // U+10400 as CSEU-8
				(byte) 0xED, (byte) 0xB0, (byte) 0x80,
				(byte) 0xC8, (byte) 0x85 // U+0205
		};
		StringUtils.fromUtf8(utf8);
	}

	@Test
	public void testFromUtf8EmptyInput() throws Exception {
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

	@Test(expected = FormatException.class)
	public void testMacToBytesRejectsShortMac() throws FormatException {
		StringUtils.macToBytes("00:00:00:00:00");
	}

	@Test(expected = FormatException.class)
	public void testMacToBytesRejectsLongMac() throws FormatException {
		StringUtils.macToBytes("00:00:00:00:00:00:00");
	}

	@Test(expected = FormatException.class)
	public void testMacToBytesRejectsInvalidCharacter() throws FormatException {
		StringUtils.macToBytes("00:00:00:00:00:0g");
	}

	@Test(expected = FormatException.class)
	public void testMacToBytesRejectsInvalidFormat() throws FormatException {
		StringUtils.macToBytes("0:000:00:00:00:00");
	}

	@Test
	public void testMacToBytesUpperCase() throws FormatException {
		byte[] expected = new byte[] {0x0A, 0x1B, 0x2C, 0x3D, 0x4E, 0x5F};
		String mac = "0A:1B:2C:3D:4E:5F";
		assertArrayEquals(expected, StringUtils.macToBytes(mac));
	}

	@Test
	public void testMacToBytesLowerCase() throws FormatException {
		byte[] expected = new byte[] {0x0A, 0x1B, 0x2C, 0x3D, 0x4E, 0x5F};
		String mac = "0a:1b:2c:3d:4e:5f";
		assertArrayEquals(expected, StringUtils.macToBytes(mac));
	}

	@Test(expected = FormatException.class)
	public void testMacToStringRejectsShortMac() throws FormatException {
		StringUtils.macToString(new byte[5]);
	}

	@Test(expected = FormatException.class)
	public void testMacToStringRejectsLongMac() throws FormatException {
		StringUtils.macToString(new byte[7]);
	}

	@Test
	public void testMacToString() throws FormatException {
		byte[] mac = new byte[] {0x0a, 0x1b, 0x2c, 0x3d, 0x4e, 0x5f};
		String expected = "0A:1B:2C:3D:4E:5F";
		assertEquals(expected, StringUtils.macToString(mac));
	}
}
