package org.briarproject.bramble.util;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import static org.briarproject.bramble.util.ByteUtils.MAX_16_BIT_UNSIGNED;
import static org.briarproject.bramble.util.ByteUtils.MAX_32_BIT_UNSIGNED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ByteUtilsTest extends BrambleTestCase {

	@Test
	public void testReadUint16() {
		byte[] b = StringUtils.fromHexString("00000000");
		assertEquals(0, ByteUtils.readUint16(b, 1));
		b = StringUtils.fromHexString("00000100");
		assertEquals(1, ByteUtils.readUint16(b, 1));
		b = StringUtils.fromHexString("007FFF00");
		assertEquals(Short.MAX_VALUE, ByteUtils.readUint16(b, 1));
		b = StringUtils.fromHexString("00FFFF00");
		assertEquals(65535, ByteUtils.readUint16(b, 1));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReadUint16ValidatesArguments1() {
		ByteUtils.readUint16(new byte[1], 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReadUint16ValidatesArguments2() {
		ByteUtils.readUint16(new byte[2], 1);
	}

	@Test
	public void testReadUint32() {
		byte[] b = StringUtils.fromHexString("000000000000");
		assertEquals(0, ByteUtils.readUint32(b, 1));
		b = StringUtils.fromHexString("000000000100");
		assertEquals(1, ByteUtils.readUint32(b, 1));
		b = StringUtils.fromHexString("007FFFFFFF00");
		assertEquals(Integer.MAX_VALUE, ByteUtils.readUint32(b, 1));
		b = StringUtils.fromHexString("00FFFFFFFF00");
		assertEquals(4294967295L, ByteUtils.readUint32(b, 1));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReadUint32ValidatesArguments1() {
		ByteUtils.readUint32(new byte[3], 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReadUint32ValidatesArguments2() {
		ByteUtils.readUint32(new byte[4], 1);
	}

	@Test
	public void testReadUint64() {
		byte[] b = StringUtils.fromHexString("00000000000000000000");
		assertEquals(0L, ByteUtils.readUint64(b, 1));
		b = StringUtils.fromHexString("00000000000000000100");
		assertEquals(1L, ByteUtils.readUint64(b, 1));
		b = StringUtils.fromHexString("007FFFFFFFFFFFFFFF00");
		assertEquals(Long.MAX_VALUE, ByteUtils.readUint64(b, 1));
		b = StringUtils.fromHexString("00800000000000000000");
		assertEquals(Long.MIN_VALUE, ByteUtils.readUint64(b, 1));
		b = StringUtils.fromHexString("00FFFFFFFFFFFFFFFF00");
		assertEquals(-1L, ByteUtils.readUint64(b, 1));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReadUint64ValidatesArguments1() {
		ByteUtils.readUint64(new byte[7], 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReadUint64ValidatesArguments2() {
		ByteUtils.readUint64(new byte[8], 1);
	}

	@Test
	public void testWriteUint16() {
		byte[] b = new byte[4];
		ByteUtils.writeUint16(0, b, 1);
		assertEquals("00000000", StringUtils.toHexString(b));
		ByteUtils.writeUint16(1, b, 1);
		assertEquals("00000100", StringUtils.toHexString(b));
		ByteUtils.writeUint16(Short.MAX_VALUE, b, 1);
		assertEquals("007FFF00", StringUtils.toHexString(b));
		ByteUtils.writeUint16(MAX_16_BIT_UNSIGNED, b, 1);
		assertEquals("00FFFF00", StringUtils.toHexString(b));
	}

	@Test
	public void testWriteUint16ValidatesArguments() {
		try {
			ByteUtils.writeUint16(0, new byte[1], 0);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
		try {
			ByteUtils.writeUint16(0, new byte[2], 1);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
		try {
			ByteUtils.writeUint16(-1, new byte[2], 0);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
		try {
			ByteUtils.writeUint16(MAX_16_BIT_UNSIGNED + 1, new byte[2], 0);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
	}

	@Test
	public void testWriteUint32() {
		byte[] b = new byte[6];
		ByteUtils.writeUint32(0, b, 1);
		assertEquals("000000000000", StringUtils.toHexString(b));
		ByteUtils.writeUint32(1, b, 1);
		assertEquals("000000000100", StringUtils.toHexString(b));
		ByteUtils.writeUint32(Integer.MAX_VALUE, b, 1);
		assertEquals("007FFFFFFF00", StringUtils.toHexString(b));
		ByteUtils.writeUint32(MAX_32_BIT_UNSIGNED, b, 1);
		assertEquals("00FFFFFFFF00", StringUtils.toHexString(b));
	}

	@Test
	public void testWriteUint32ValidatesArguments() {
		try {
			ByteUtils.writeUint32(0, new byte[3], 0);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
		try {
			ByteUtils.writeUint32(0, new byte[4], 1);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
		try {
			ByteUtils.writeUint32(-1, new byte[4], 0);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
		try {
			ByteUtils.writeUint32(MAX_32_BIT_UNSIGNED + 1, new byte[4], 0);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
	}

	@Test
	public void testWriteUint64() {
		byte[] b = new byte[10];
		ByteUtils.writeUint64(0, b, 1);
		assertEquals("00000000000000000000", StringUtils.toHexString(b));
		ByteUtils.writeUint64(1, b, 1);
		assertEquals("00000000000000000100", StringUtils.toHexString(b));
		ByteUtils.writeUint64(Long.MAX_VALUE, b, 1);
		assertEquals("007FFFFFFFFFFFFFFF00", StringUtils.toHexString(b));
	}

	@Test
	public void testWriteUint64ValidatesArguments() {
		try {
			ByteUtils.writeUint64(0, new byte[7], 0);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
		try {
			ByteUtils.writeUint64(0, new byte[8], 1);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
		try {
			ByteUtils.writeUint64(-1, new byte[8], 0);
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
			assertEquals(1 << i, ByteUtils.readUint(b, i + 1));
		}
		b = new byte[2];
		for (int i = 0; i < 65535; i++) {
			ByteUtils.writeUint16(i, b, 0);
			assertEquals(i, ByteUtils.readUint(b, 16));
			assertEquals(i >> 1, ByteUtils.readUint(b, 15));
		}
	}
}
