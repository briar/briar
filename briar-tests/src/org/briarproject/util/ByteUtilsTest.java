package org.briarproject.util;

import org.briarproject.BriarTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ByteUtilsTest extends BriarTestCase {

	@Test
	public void testReadUint16() {
		byte[] b = StringUtils.fromHexString("000000");
		assertEquals(0, ByteUtils.readUint16(b, 1));
		b = StringUtils.fromHexString("000001");
		assertEquals(1, ByteUtils.readUint16(b, 1));
		b = StringUtils.fromHexString("00FFFF");
		assertEquals(65535, ByteUtils.readUint16(b, 1));
	}

	@Test
	public void testReadUint32() {
		byte[] b = StringUtils.fromHexString("0000000000");
		assertEquals(0, ByteUtils.readUint32(b, 1));
		b = StringUtils.fromHexString("0000000001");
		assertEquals(1, ByteUtils.readUint32(b, 1));
		b = StringUtils.fromHexString("00FFFFFFFF");
		assertEquals(4294967295L, ByteUtils.readUint32(b, 1));
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
		ByteUtils.writeUint16(ByteUtils.MAX_16_BIT_UNSIGNED, b, 1);
		assertEquals("00FFFF00", StringUtils.toHexString(b));
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
		ByteUtils.writeUint32(ByteUtils.MAX_32_BIT_UNSIGNED, b, 1);
		assertEquals("00FFFFFFFF00", StringUtils.toHexString(b));
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
