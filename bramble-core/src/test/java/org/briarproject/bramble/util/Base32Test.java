package org.briarproject.bramble.util;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class Base32Test extends BrambleTestCase {

	// Test vectors from RFC 4648
	// https://tools.ietf.org/html/rfc4648#section-10

	@Test
	public void testEncoding() {
		assertEquals("", Base32.encode(new byte[0]));
		assertEquals("MY", Base32.encode(new byte[] {'f'}));
		assertEquals("MZXQ", Base32.encode(new byte[] {'f', 'o'}));
		assertEquals("MZXW6", Base32.encode(new byte[] {'f', 'o', 'o'}));
		assertEquals("MZXW6YQ", Base32.encode(new byte[] {'f', 'o', 'o', 'b'}));
		assertEquals("MZXW6YTB",
				Base32.encode(new byte[] {'f', 'o', 'o', 'b', 'a'}));
		assertEquals("MZXW6YTBOI",
				Base32.encode(new byte[] {'f', 'o', 'o', 'b', 'a', 'r'}));
	}

	@Test
	public void testStrictDecoding() {
		testDecoding(true);
	}

	@Test
	public void testNonStrictDecoding() {
		testDecoding(false);
	}

	private void testDecoding(boolean strict) {
		assertArrayEquals(new byte[0], Base32.decode("", strict));
		assertArrayEquals(new byte[] {'f'}, Base32.decode("MY", strict));
		assertArrayEquals(new byte[] {'f', 'o'}, Base32.decode("MZXQ", strict));
		assertArrayEquals(new byte[] {'f', 'o', 'o'},
				Base32.decode("MZXW6", strict));
		assertArrayEquals(new byte[] {'f', 'o', 'o', 'b'},
				Base32.decode("MZXW6YQ", strict));
		assertArrayEquals(new byte[] {'f', 'o', 'o', 'b', 'a'},
				Base32.decode("MZXW6YTB", strict));
		assertArrayEquals(new byte[] {'f', 'o', 'o', 'b', 'a', 'r'},
				Base32.decode("MZXW6YTBOI", strict));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testStrictDecodingRejectsNonZeroUnusedBits() {
		Base32.decode("MZ", true);
	}

	@Test
	public void testNonStrictDecodingAcceptsNonZeroUnusedBits() {
		assertArrayEquals(new byte[] {'f'}, Base32.decode("MZ", false));
	}

	@Test
	public void testRoundTrip() {
		Random random = new Random();
		byte[] data = new byte[100 + random.nextInt(100)];
		random.nextBytes(data);
		assertArrayEquals(data, Base32.decode(Base32.encode(data), true));
		assertArrayEquals(data, Base32.decode(Base32.encode(data), false));
	}
}
