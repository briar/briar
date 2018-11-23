package org.briarproject.bramble.util;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import static org.briarproject.bramble.util.ByteUtils.MAX_16_BIT_UNSIGNED;
import static org.briarproject.bramble.util.ByteUtils.MAX_32_BIT_UNSIGNED;
import static org.briarproject.bramble.util.ByteUtils.readUint;
import static org.briarproject.bramble.util.ByteUtils.readUint16;
import static org.briarproject.bramble.util.ByteUtils.readUint32;
import static org.briarproject.bramble.util.ByteUtils.readUint64;
import static org.briarproject.bramble.util.ByteUtils.writeUint16;
import static org.briarproject.bramble.util.ByteUtils.writeUint32;
import static org.briarproject.bramble.util.ByteUtils.writeUint64;
import static org.briarproject.bramble.util.StringUtils.fromHexString;
import static org.briarproject.bramble.util.StringUtils.toHexString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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
	public void testReadUint() {
		byte[] b = new byte[1];
		b[0] = (byte) 128;
		for (int i = 0; i < 8; i++) {
			assertEquals(1 << i, readUint(b, i + 1));
		}
		b = new byte[2];
		for (int i = 0; i < 65535; i++) {
			writeUint16(i, b, 0);
			assertEquals(i, readUint(b, 16));
			assertEquals(i >> 1, readUint(b, 15));
		}
	}
}
