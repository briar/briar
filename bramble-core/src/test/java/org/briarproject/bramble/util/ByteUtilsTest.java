package org.briarproject.bramble.util;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.util.Random;

import static java.util.Arrays.fill;
import static org.briarproject.bramble.util.ByteUtils.MAX_16_BIT_UNSIGNED;
import static org.briarproject.bramble.util.ByteUtils.MAX_32_BIT_UNSIGNED;
import static org.briarproject.bramble.util.ByteUtils.MAX_VARINT_BYTES;
import static org.briarproject.bramble.util.ByteUtils.getVarIntBytes;
import static org.briarproject.bramble.util.ByteUtils.readUint16;
import static org.briarproject.bramble.util.ByteUtils.readUint32;
import static org.briarproject.bramble.util.ByteUtils.readUint64;
import static org.briarproject.bramble.util.ByteUtils.readVarInt;
import static org.briarproject.bramble.util.ByteUtils.writeUint16;
import static org.briarproject.bramble.util.ByteUtils.writeUint32;
import static org.briarproject.bramble.util.ByteUtils.writeUint64;
import static org.briarproject.bramble.util.ByteUtils.writeVarInt;
import static org.briarproject.bramble.util.StringUtils.fromHexString;
import static org.briarproject.bramble.util.StringUtils.toHexString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class ByteUtilsTest extends BrambleTestCase {

	@Test
	public void testReadUint16() {
		byte[] b = fromHexString("00000000");
		assertEquals(0, readUint16(b, 1));
		b = fromHexString("00000100");
		assertEquals(1, readUint16(b, 1));
		b = fromHexString("007FFF00");
		assertEquals(Short.MAX_VALUE, readUint16(b, 1));
		b = fromHexString("00FFFF00");
		assertEquals(65535, readUint16(b, 1));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReadUint16ValidatesArguments1() {
		readUint16(new byte[1], 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReadUint16ValidatesArguments2() {
		readUint16(new byte[2], 1);
	}

	@Test
	public void testReadUint32() {
		byte[] b = fromHexString("000000000000");
		assertEquals(0, readUint32(b, 1));
		b = fromHexString("000000000100");
		assertEquals(1, readUint32(b, 1));
		b = fromHexString("007FFFFFFF00");
		assertEquals(Integer.MAX_VALUE, readUint32(b, 1));
		b = fromHexString("00FFFFFFFF00");
		assertEquals(4294967295L, readUint32(b, 1));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReadUint32ValidatesArguments1() {
		readUint32(new byte[3], 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReadUint32ValidatesArguments2() {
		readUint32(new byte[4], 1);
	}

	@Test
	public void testReadUint64() {
		byte[] b = fromHexString("00000000000000000000");
		assertEquals(0L, readUint64(b, 1));
		b = fromHexString("00000000000000000100");
		assertEquals(1L, readUint64(b, 1));
		b = fromHexString("007FFFFFFFFFFFFFFF00");
		assertEquals(Long.MAX_VALUE, readUint64(b, 1));
		b = fromHexString("00800000000000000000");
		assertEquals(Long.MIN_VALUE, readUint64(b, 1));
		b = fromHexString("00FFFFFFFFFFFFFFFF00");
		assertEquals(-1L, readUint64(b, 1));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReadUint64ValidatesArguments1() {
		readUint64(new byte[7], 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReadUint64ValidatesArguments2() {
		readUint64(new byte[8], 1);
	}

	@Test
	public void testWriteUint16() {
		byte[] b = new byte[4];
		writeUint16(0, b, 1);
		assertEquals("00000000", toHexString(b));
		writeUint16(1, b, 1);
		assertEquals("00000100", toHexString(b));
		writeUint16(Short.MAX_VALUE, b, 1);
		assertEquals("007FFF00", toHexString(b));
		writeUint16(MAX_16_BIT_UNSIGNED, b, 1);
		assertEquals("00FFFF00", toHexString(b));
	}

	@Test
	public void testWriteUint16ValidatesArguments() {
		try {
			writeUint16(0, new byte[1], 0);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
		try {
			writeUint16(0, new byte[2], 1);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
		try {
			writeUint16(-1, new byte[2], 0);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
		try {
			writeUint16(MAX_16_BIT_UNSIGNED + 1, new byte[2], 0);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
	}

	@Test
	public void testWriteUint32() {
		byte[] b = new byte[6];
		writeUint32(0, b, 1);
		assertEquals("000000000000", toHexString(b));
		writeUint32(1, b, 1);
		assertEquals("000000000100", toHexString(b));
		writeUint32(Integer.MAX_VALUE, b, 1);
		assertEquals("007FFFFFFF00", toHexString(b));
		writeUint32(MAX_32_BIT_UNSIGNED, b, 1);
		assertEquals("00FFFFFFFF00", toHexString(b));
	}

	@Test
	public void testWriteUint32ValidatesArguments() {
		try {
			writeUint32(0, new byte[3], 0);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
		try {
			writeUint32(0, new byte[4], 1);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
		try {
			writeUint32(-1, new byte[4], 0);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
		try {
			writeUint32(MAX_32_BIT_UNSIGNED + 1, new byte[4], 0);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
	}

	@Test
	public void testWriteUint64() {
		byte[] b = new byte[10];
		writeUint64(0, b, 1);
		assertEquals("00000000000000000000", toHexString(b));
		writeUint64(1, b, 1);
		assertEquals("00000000000000000100", toHexString(b));
		writeUint64(Long.MAX_VALUE, b, 1);
		assertEquals("007FFFFFFFFFFFFFFF00", toHexString(b));
	}

	@Test
	public void testWriteUint64ValidatesArguments() {
		try {
			writeUint64(0, new byte[7], 0);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
		try {
			writeUint64(0, new byte[8], 1);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
		try {
			writeUint64(-1, new byte[8], 0);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
	}

	@Test
	public void testGetVarIntBytesToWrite() {
		assertEquals(1, getVarIntBytes(0));
		assertEquals(1, getVarIntBytes(0x7F)); // Max 7-bit int
		assertEquals(2, getVarIntBytes(0x7F + 1));
		assertEquals(2, getVarIntBytes(0x3FFF)); // Max 14-bit int
		assertEquals(3, getVarIntBytes(0x3FFF + 1));
		assertEquals(3, getVarIntBytes(0x1FFFFF)); // Max 21-bit int
		assertEquals(4, getVarIntBytes(0x1FFFFF + 1));
		assertEquals(4, getVarIntBytes(0xFFFFFFF)); // Max 28-bit int
		assertEquals(5, getVarIntBytes(0xFFFFFFF + 1));
		assertEquals(5, getVarIntBytes(0x7FFFFFFFFL)); // Max 35-bit int
		assertEquals(6, getVarIntBytes(0x7FFFFFFFFL + 1));
		assertEquals(6, getVarIntBytes(0x3FFFFFFFFFFL)); // Max 42-bit int
		assertEquals(7, getVarIntBytes(0x3FFFFFFFFFFL + 1));
		assertEquals(7, getVarIntBytes(0x1FFFFFFFFFFFFL)); // Max 49-bit int
		assertEquals(8, getVarIntBytes(0x1FFFFFFFFFFFFL + 1));
		assertEquals(8, getVarIntBytes(0xFFFFFFFFFFFFFFL)); // Max 56-bit int
		assertEquals(9, getVarIntBytes(0xFFFFFFFFFFFFFFL + 1));
		assertEquals(9, getVarIntBytes(0x7FFFFFFFFFFFFFFFL)); // Max 63-bit int
		assertEquals(MAX_VARINT_BYTES, getVarIntBytes(Long.MAX_VALUE)); // Same
	}

	@Test
	public void testWriteVarInt() {
		testWriteVarInt(0, 1, "00");
		testWriteVarInt(1, 1, "01");
		testWriteVarInt(0x7F, 1, "7F"); // Max 7-bit int
		testWriteVarInt(0x7F + 1, 2, "8100");
		testWriteVarInt(0x3FFF, 2, "FF7F"); // Max 14-bit int
		testWriteVarInt(0x3FFF + 1, 3, "818000");
		testWriteVarInt(0x1FFFFF, 3, "FFFF7F"); // Max 21-bit int
		testWriteVarInt(0x1FFFFF + 1, 4, "81808000");
		testWriteVarInt(0xFFFFFFF, 4, "FFFFFF7F"); // Max 28-bit int
		testWriteVarInt(0xFFFFFFF + 1, 5, "8180808000");
		testWriteVarInt(0x7FFFFFFFFL, 5, "FFFFFFFF7F"); // Max 35-bit int
		testWriteVarInt(0x7FFFFFFFFL + 1, 6, "818080808000");
		testWriteVarInt(0x3FFFFFFFFFFL, 6, "FFFFFFFFFF7F"); // Max 42-bit int
		testWriteVarInt(0x3FFFFFFFFFFL + 1, 7, "81808080808000");
		testWriteVarInt(0x1FFFFFFFFFFFFL, 7, "FFFFFFFFFFFF7F"); // Max 49
		testWriteVarInt(0x1FFFFFFFFFFFFL + 1, 8, "8180808080808000");
		testWriteVarInt(0xFFFFFFFFFFFFFFL, 8, "FFFFFFFFFFFFFF7F"); // Max 56
		testWriteVarInt(0xFFFFFFFFFFFFFFL + 1, 9, "818080808080808000");
		testWriteVarInt(0x7FFFFFFFFFFFFFFFL, 9, "FFFFFFFFFFFFFFFF7F"); // Max 63
		testWriteVarInt(Long.MAX_VALUE, MAX_VARINT_BYTES, "FFFFFFFFFFFFFFFF7F");
	}

	private void testWriteVarInt(long src, int len, String destHex) {
		byte[] dest = new byte[9];
		assertEquals(len, writeVarInt(src, dest, 0));
		assertEquals(destHex, toHexString(dest).substring(0, len * 2));
	}

	@Test
	public void testGetVarIntBytesToRead() throws FormatException {
		testGetVarIntBytesToRead(1, "00", 0);
		testGetVarIntBytesToRead(1, "01", 0);
		testGetVarIntBytesToRead(1, "7F", 0); // Max 7-bit int
		testGetVarIntBytesToRead(2, "8100", 0);
		testGetVarIntBytesToRead(2, "FF7F", 0); // Max 14-bit int
		testGetVarIntBytesToRead(3, "818000", 0);
		testGetVarIntBytesToRead(3, "FFFF7F", 0); // Max 21-bit int
		testGetVarIntBytesToRead(4, "81808000", 0);
		testGetVarIntBytesToRead(4, "FFFFFF7F", 0); // Max 28-bit int
		testGetVarIntBytesToRead(5, "8180808000", 0);
		testGetVarIntBytesToRead(5, "FFFFFFFF7F", 0); // Max 35-bit int
		testGetVarIntBytesToRead(6, "818080808000", 0);
		testGetVarIntBytesToRead(6, "FFFFFFFFFF7F", 0); // Max 42-bit int
		testGetVarIntBytesToRead(7, "81808080808000", 0);
		testGetVarIntBytesToRead(7, "FFFFFFFFFFFF7F", 0); // Max 49-bit int
		testGetVarIntBytesToRead(8, "8180808080808000", 0);
		testGetVarIntBytesToRead(8, "FFFFFFFFFFFFFF7F", 0); // Max 56-bit int
		testGetVarIntBytesToRead(9, "818080808080808000", 0);
		testGetVarIntBytesToRead(9, "FFFFFFFFFFFFFFFF7F", 0); // Max 63-bit int
		// Start at offset, ignore trailing data
		testGetVarIntBytesToRead(1, "FF0000", 1);
		testGetVarIntBytesToRead(9, "00FFFFFFFFFFFFFFFF7F00", 1);
	}

	private void testGetVarIntBytesToRead(int len, String srcHex, int offset)
			throws FormatException {
		assertEquals(len, getVarIntBytes(fromHexString(srcHex), offset));
	}

	@Test(expected = FormatException.class)
	public void testGetVarIntBytesToReadThrowsExceptionAtEndOfInput()
			throws FormatException {
		byte[] src = new byte[MAX_VARINT_BYTES - 1];
		fill(src, (byte) 0xFF);
		// Reaches end of input without finding lowered continuation flag
		getVarIntBytes(src, 0);
	}

	@Test(expected = FormatException.class)
	public void testGetVarIntBytesToReadThrowsExceptionAfterNineBytes()
			throws FormatException {
		byte[] src = new byte[MAX_VARINT_BYTES];
		fill(src, (byte) 0xFF);
		// Reaches max length without finding lowered continuation flag
		getVarIntBytes(src, 0);
	}

	@Test
	public void testReadVarInt() throws FormatException {
		testReadVarInt(0, "00", 0);
		testReadVarInt(1, "01", 0);
		testReadVarInt(0x7F, "7F", 0); // Max 7-bit int
		testReadVarInt(0x7F + 1, "8100", 0);
		testReadVarInt(0x3FFF, "FF7F", 0); // Max 14-bit int
		testReadVarInt(0x3FFF + 1, "818000", 0);
		testReadVarInt(0x1FFFFF, "FFFF7F", 0); // Max 21-bit int
		testReadVarInt(0x1FFFFF + 1, "81808000", 0);
		testReadVarInt(0xFFFFFFF, "FFFFFF7F", 0); // Max 28-bit int
		testReadVarInt(0xFFFFFFF + 1, "8180808000", 0);
		testReadVarInt(0x7FFFFFFFFL, "FFFFFFFF7F", 0); // Max 35-bit int
		testReadVarInt(0x7FFFFFFFFL + 1, "818080808000", 0);
		testReadVarInt(0x3FFFFFFFFFFL, "FFFFFFFFFF7F", 0); // Max 42-bit int
		testReadVarInt(0x3FFFFFFFFFFL + 1, "81808080808000", 0);
		testReadVarInt(0x1FFFFFFFFFFFFL, "FFFFFFFFFFFF7F", 0); // Max 49-bit int
		testReadVarInt(0x1FFFFFFFFFFFFL + 1, "8180808080808000", 0);
		testReadVarInt(0xFFFFFFFFFFFFFFL, "FFFFFFFFFFFFFF7F", 0); // Max 56
		testReadVarInt(0xFFFFFFFFFFFFFFL + 1, "818080808080808000", 0);
		testReadVarInt(0x7FFFFFFFFFFFFFFFL, "FFFFFFFFFFFFFFFF7F", 0); // Max 63
		testReadVarInt(Long.MAX_VALUE, "FFFFFFFFFFFFFFFF7F", 0);
		// Start at offset, ignore trailing data
		testReadVarInt(0, "FF0000", 1);
		testReadVarInt(Long.MAX_VALUE, "00FFFFFFFFFFFFFFFF7F00", 1);
	}

	private void testReadVarInt(long dest, String srcHex, int offset)
			throws FormatException {
		assertEquals(dest, readVarInt(fromHexString(srcHex), offset));
	}

	@Test(expected = FormatException.class)
	public void testReadVarIntThrowsExceptionAtEndOfInput()
			throws FormatException {
		byte[] src = new byte[MAX_VARINT_BYTES - 1];
		fill(src, (byte) 0xFF);
		// Reaches end of input without finding lowered continuation flag
		readVarInt(src, 0);
	}

	@Test(expected = FormatException.class)
	public void testReadVarIntThrowsExceptionAfterNineBytes()
			throws FormatException {
		byte[] src = new byte[MAX_VARINT_BYTES];
		fill(src, (byte) 0xFF);
		// Reaches max length without finding lowered continuation flag
		readVarInt(src, 0);
	}

	@Test
	public void testWriteAndReadVarInt() throws FormatException {
		Random random = new Random();
		int padding = 10;
		byte[] buf = new byte[MAX_VARINT_BYTES + padding];
		for (int i = 0; i < 1000; i++) {
			long src = random.nextLong() & 0x7FFFFFFFFFFFFFFFL; // Non-negative
			int offset = random.nextInt(padding);
			int len = getVarIntBytes(src);
			assertEquals(len, writeVarInt(src, buf, offset));
			assertEquals(len, getVarIntBytes(buf, offset));
			assertEquals(src, readVarInt(buf, offset));
			fill(buf, (byte) 0);
		}
	}
}
