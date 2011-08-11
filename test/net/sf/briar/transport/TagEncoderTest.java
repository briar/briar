package net.sf.briar.transport;

import net.sf.briar.util.StringUtils;

import org.junit.Test;

import junit.framework.TestCase;

public class TagEncoderTest extends TestCase {

	@Test
	public void testWriteUint16() throws Exception {
		byte[] b = new byte[3];
		TagEncoder.writeUint16(0, b, 1);
		assertEquals("000000", StringUtils.toHexString(b));
		TagEncoder.writeUint16(1, b, 1);
		assertEquals("000001", StringUtils.toHexString(b));
		TagEncoder.writeUint16(65535, b, 1);
		assertEquals("00FFFF", StringUtils.toHexString(b));
	}

	@Test
	public void testWriteUint32() throws Exception {
		byte[] b = new byte[5];
		TagEncoder.writeUint32(0L, b, 1);
		assertEquals("0000000000", StringUtils.toHexString(b));
		TagEncoder.writeUint32(1L, b, 1);
		assertEquals("0000000001", StringUtils.toHexString(b));
		TagEncoder.writeUint32(4294967295L, b, 1);
		assertEquals("00FFFFFFFF", StringUtils.toHexString(b));
	}
}
