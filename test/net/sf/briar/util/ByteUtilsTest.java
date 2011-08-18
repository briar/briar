package net.sf.briar.util;

import junit.framework.TestCase;

import org.junit.Test;

public class ByteUtilsTest extends TestCase {

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
		assertEquals(0L, ByteUtils.readUint32(b, 1));
		b = StringUtils.fromHexString("0000000001");
		assertEquals(1L, ByteUtils.readUint32(b, 1));
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
		ByteUtils.writeUint32(0L, b, 1);
		assertEquals("0000000000", StringUtils.toHexString(b));
		ByteUtils.writeUint32(1L, b, 1);
		assertEquals("0000000001", StringUtils.toHexString(b));
		ByteUtils.writeUint32(4294967295L, b, 1);
		assertEquals("00FFFFFFFF", StringUtils.toHexString(b));
	}
}
