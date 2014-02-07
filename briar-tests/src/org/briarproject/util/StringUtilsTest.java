package org.briarproject.util;

import static org.junit.Assert.assertArrayEquals;
import org.briarproject.BriarTestCase;

import org.junit.Test;

public class StringUtilsTest extends BriarTestCase {

	@Test
	public void testToHexString() {
		byte[] b = new byte[] {1, 2, 3, 127, -128};
		String s = StringUtils.toHexString(b);
		assertEquals("0102037F80", s);
	}

	@Test
	public void testFromHexString() {
		try {
			StringUtils.fromHexString("12345");
			fail();
		} catch(IllegalArgumentException expected) {}
		try {
			StringUtils.fromHexString("ABCDEFGH");
			fail();
		} catch(IllegalArgumentException expected) {}
		byte[] b = StringUtils.fromHexString("0102037F80");
		assertArrayEquals(new byte[] {1, 2, 3, 127, -128}, b);
		b = StringUtils.fromHexString("0a0b0c0d0e0f");
		assertArrayEquals(new byte[] {10, 11, 12, 13, 14, 15}, b);
	}
}
