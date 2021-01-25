package org.briarproject.briar.attachment;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CountingInputStreamTest extends BrambleTestCase {

	private final Random random = new Random();
	private final byte[] src = getRandomBytes(123);

	@Test
	public void testCountsSingleByteReads() throws Exception {
		InputStream delegate = new ByteArrayInputStream(src);
		// The limit is high enough to read the whole src array
		CountingInputStream in =
				new CountingInputStream(delegate, src.length + 1);

		// No bytes should have been read initially
		assertEquals(0L, in.getBytesRead());
		// The reads should return the contents of the src array
		for (int i = 0; i < src.length; i++) {
			assertEquals(i, in.getBytesRead());
			assertEquals(src[i] & 0xFF, in.read());
		}
		// The count should match the length of the src array
		assertEquals(src.length, in.getBytesRead());
		// Trying to read another byte should return EOF
		assertEquals(-1, in.read());
		// Reading EOF shouldn't affect the count
		assertEquals(src.length, in.getBytesRead());
	}

	@Test
	public void testCountsMultiByteReads() throws Exception {
		InputStream delegate = new ByteArrayInputStream(src);
		// The limit is high enough to read the whole src array
		CountingInputStream in =
				new CountingInputStream(delegate, src.length + 1);

		// No bytes should have been read initially
		assertEquals(0L, in.getBytesRead());
		// Copy the src array in random-sized pieces
		byte[] dest = new byte[src.length];
		int offset = 0;
		while (offset < dest.length) {
			assertEquals(offset, in.getBytesRead());
			int length = Math.min(random.nextInt(10), dest.length - offset);
			assertEquals(length, in.read(dest, offset, length));
			offset += length;
		}
		// The dest array should be a copy of the src array
		assertArrayEquals(src, dest);
		// The count should match the length of the src array
		assertEquals(src.length, in.getBytesRead());
		// Trying to read another byte should return EOF
		assertEquals(-1, in.read(dest, 0, 1));
		// Reading EOF shouldn't affect the count
		assertEquals(src.length, in.getBytesRead());
	}

	@Test
	public void testCountsSkips() throws Exception {
		InputStream delegate = new ByteArrayInputStream(src);
		// The limit is high enough to read the whole src array
		CountingInputStream in =
				new CountingInputStream(delegate, src.length + 1);

		// No bytes should have been read initially
		assertEquals(0L, in.getBytesRead());
		// Skip the src array in random-sized pieces
		int offset = 0;
		while (offset < src.length) {
			assertEquals(offset, in.getBytesRead());
			int length = Math.min(random.nextInt(10), src.length - offset);
			assertEquals(length, in.skip(length));
			offset += length;
		}
		// The count should match the length of the src array
		assertEquals(src.length, in.getBytesRead());
		// Trying to skip another byte should return zero
		assertEquals(0, in.skip(1));
		// Returning zero shouldn't affect the count
		assertEquals(src.length, in.getBytesRead());
	}

	@Test
	public void testReturnsEofWhenSingleByteReadReachesLimit()
			throws Exception {
		InputStream delegate = new ByteArrayInputStream(src);
		// The limit is one byte lower than the length of the src array
		CountingInputStream in =
				new CountingInputStream(delegate, src.length - 1);

		// The reads should return the contents of the src array, except the
		// last byte
		for (int i = 0; i < src.length - 1; i++) {
			assertEquals(src[i] & 0xFF, in.read());
		}
		// The count should match the limit
		assertEquals(src.length - 1, in.getBytesRead());
		// Trying to read another byte should return EOF
		assertEquals(-1, in.read());
		// Reading EOF shouldn't affect the count
		assertEquals(src.length - 1, in.getBytesRead());
	}

	@Test
	public void testReturnsEofWhenMultiByteReadReachesLimit() throws Exception {
		InputStream delegate = new ByteArrayInputStream(src);
		// The limit is one byte lower than the length of the src array
		CountingInputStream in =
				new CountingInputStream(delegate, src.length - 1);

		// Copy the src array in random-sized pieces, except the last two bytes
		byte[] dest = new byte[src.length];
		int offset = 0;
		while (offset < dest.length - 2) {
			int length = Math.min(random.nextInt(10), dest.length - 2 - offset);
			assertEquals(length, in.read(dest, offset, length));
			offset += length;
		}
		// Trying to read two bytes should only return one, reaching the limit
		assertEquals(1, in.read(dest, offset, 2));
		// The dest array should be a copy of the src array, except the last
		// byte
		for (int i = 0; i < src.length - 1; i++) assertEquals(src[i], dest[i]);
		// The count should match the limit
		assertEquals(src.length - 1, in.getBytesRead());
		// Trying to read another byte should return EOF
		assertEquals(-1, in.read(dest, 0, 1));
		// Reading EOF shouldn't affect the count
		assertEquals(src.length - 1, in.getBytesRead());
	}

	@Test
	public void testReturnsZeroWhenSkipReachesLimit() throws Exception {
		InputStream delegate = new ByteArrayInputStream(src);
		// The limit is one byte lower than the length of the src array
		CountingInputStream in =
				new CountingInputStream(delegate, src.length - 1);

		// Skip the src array in random-sized pieces, except the last two bytes
		int offset = 0;
		while (offset < src.length - 2) {
			assertEquals(offset, in.getBytesRead());
			int length = Math.min(random.nextInt(10), src.length - 2 - offset);
			assertEquals(length, in.skip(length));
			offset += length;
		}
		// Trying to skip two bytes should only skip one, reaching the limit
		assertEquals(1, in.skip(2));
		// The count should match the limit
		assertEquals(src.length - 1, in.getBytesRead());
		// Trying to skip another byte should return zero
		assertEquals(0, in.skip(1));
		// Returning zero shouldn't affect the count
		assertEquals(src.length - 1, in.getBytesRead());
	}

	@Test
	public void testMarkIsNotSupported() {
		InputStream delegate = new ByteArrayInputStream(src);
		CountingInputStream in = new CountingInputStream(delegate, src.length);
		assertFalse(in.markSupported());
	}

	@Test(expected = IOException.class)
	public void testResetIsNotSupported() throws Exception {
		InputStream delegate = new ByteArrayInputStream(src);
		CountingInputStream in = new CountingInputStream(delegate, src.length);
		in.mark(src.length);
		assertEquals(src.length, in.read(new byte[src.length]));
		in.reset();
	}
}
