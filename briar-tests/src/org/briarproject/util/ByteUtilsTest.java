package org.briarproject.util;

import org.briarproject.BriarTestCase;
import org.junit.Test;

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
		byte[] b = new byte[3];
		ByteUtils.writeUint16(0, b, 1);
		assertEquals("000000", StringUtils.toHexString(b));
		ByteUtils.writeUint16(1, b, 1);
		assertEquals("000001", StringUtils.toHexString(b));
		ByteUtils.writeUint16(65535, b, 1);
		assertEquals("00FFFF", StringUtils.toHexString(b));
	}

	@Test
	public void testWriteUint32() {
		byte[] b = new byte[5];
		ByteUtils.writeUint32(0, b, 1);
		assertEquals("0000000000", StringUtils.toHexString(b));
		ByteUtils.writeUint32(1, b, 1);
		assertEquals("0000000001", StringUtils.toHexString(b));
		ByteUtils.writeUint32(4294967295L, b, 1);
		assertEquals("00FFFFFFFF", StringUtils.toHexString(b));
	}

	@Test
	public void testReadUint() {
		byte[] b = new byte[1];
		b[0] = (byte) 128;
		for(int i = 0; i < 8; i++) {
			assertEquals(1 << i, ByteUtils.readUint(b, i + 1));
		}
		b = new byte[2];
		for(int i = 0; i < 65535; i++) {
			ByteUtils.writeUint16(i, b, 0);
			assertEquals(i, ByteUtils.readUint(b, 16));
			assertEquals(i >> 1, ByteUtils.readUint(b, 15));
		}
	}
}
