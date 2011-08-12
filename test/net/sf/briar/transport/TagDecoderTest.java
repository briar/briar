package net.sf.briar.transport;

import junit.framework.TestCase;

import net.sf.briar.util.StringUtils;

import org.junit.Test;

public class TagDecoderTest extends TestCase {

	@Test
	public void testReadUint16() {
		byte[] b = StringUtils.fromHexString("000000");
		assertEquals(0, TagDecoder.readUint16(b, 1));
		b = StringUtils.fromHexString("000001");
		assertEquals(1, TagDecoder.readUint16(b, 1));
		b = StringUtils.fromHexString("00FFFF");
		assertEquals(65535, TagDecoder.readUint16(b, 1));
	}

	@Test
	public void testReadUint32() {
		byte[] b = StringUtils.fromHexString("0000000000");
		assertEquals(0L, TagDecoder.readUint32(b, 1));
		b = StringUtils.fromHexString("0000000001");
		assertEquals(1L, TagDecoder.readUint32(b, 1));
		b = StringUtils.fromHexString("00FFFFFFFF");
		assertEquals(4294967295L, TagDecoder.readUint32(b, 1));
	}
}
