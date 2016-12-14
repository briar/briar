package org.briarproject.bramble.crypto;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class AsciiArmourTest extends BrambleTestCase {

	@Test
	public void testWrapOnSingleLine() {
		byte[] b = new byte[8];
		for (int i = 0; i < b.length; i++) b[i] = (byte) i;
		String expected = "0001020304050607\r\n";
		assertEquals(expected, AsciiArmour.wrap(b, 70));
	}

	@Test
	public void testWrapOnMultipleLines() {
		byte[] b = new byte[8];
		for (int i = 0; i < b.length; i++) b[i] = (byte) i;
		String expected = "0001020\r\n3040506\r\n07\r\n";
		assertEquals(expected, AsciiArmour.wrap(b, 7));
	}

	@Test
	public void testUnwrapOnSingleLine() throws Exception {
		String s = "0001020304050607";
		byte[] expected = new byte[] {0, 1, 2, 3, 4, 5, 6, 7};
		assertArrayEquals(expected, AsciiArmour.unwrap(s));
	}

	@Test
	public void testUnwrapOnMultipleLines() throws Exception {
		String s = "0001020\r\n3040506\r\n07";
		byte[] expected = new byte[] {0, 1, 2, 3, 4, 5, 6, 7};
		assertArrayEquals(expected, AsciiArmour.unwrap(s));
	}

	@Test
	public void testUnwrapWithJunkCharacters() throws Exception {
		String s = "0001??020\rzz\n30z40..506\r\n07;;";
		byte[] expected = new byte[] {0, 1, 2, 3, 4, 5, 6, 7};
		assertArrayEquals(expected, AsciiArmour.unwrap(s));
	}
}
