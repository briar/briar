package org.briarproject.transport;

import org.briarproject.BriarTestCase;
import org.junit.Test;

import java.util.Collection;

import static org.briarproject.api.transport.TransportConstants.REORDERING_WINDOW_SIZE;
import static org.briarproject.util.ByteUtils.MAX_32_BIT_UNSIGNED;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ReorderingWindowTest extends BriarTestCase {

	@Test
	public void testWindowSliding() {
		ReorderingWindow w = new ReorderingWindow();
		for (int i = 0; i < 100; i++) {
			assertFalse(w.isSeen(i));
			w.setSeen(i);
			assertTrue(w.isSeen(i));
		}
	}

	@Test
	public void testWindowJumping() {
		ReorderingWindow w = new ReorderingWindow();
		for (int i = 0; i < 100; i += 13) {
			assertFalse(w.isSeen(i));
			w.setSeen(i);
			assertTrue(w.isSeen(i));
		}
	}

	@Test
	public void testWindowUpperLimit() {
		ReorderingWindow w = new ReorderingWindow();
		// Centre is 0, highest value in window is 15
		w.setSeen(15);
		// Centre is 16, highest value in window is 31
		w.setSeen(31);
		try {
			// Centre is 32, highest value in window is 47
			w.setSeen(48);
			fail();
		} catch (IllegalArgumentException expected) {}
		// Centre is max - 1, highest value in window is max
		byte[] bitmap = new byte[REORDERING_WINDOW_SIZE / 8];
		w = new ReorderingWindow(MAX_32_BIT_UNSIGNED - 1, bitmap);
		assertFalse(w.isSeen(MAX_32_BIT_UNSIGNED - 1));
		assertFalse(w.isSeen(MAX_32_BIT_UNSIGNED));
		// Values greater than max should never be allowed
		try {
			w.setSeen(MAX_32_BIT_UNSIGNED + 1);
			fail();
		} catch (IllegalArgumentException expected) {}
		w.setSeen(MAX_32_BIT_UNSIGNED);
		assertTrue(w.isSeen(MAX_32_BIT_UNSIGNED));
		// Centre should have moved to max + 1
		assertEquals(MAX_32_BIT_UNSIGNED + 1, w.getCentre());
		// The bit corresponding to max should be set
		byte[] expectedBitmap = new byte[REORDERING_WINDOW_SIZE / 8];
		expectedBitmap[expectedBitmap.length / 2 - 1] = 1; // 00000001
		assertArrayEquals(expectedBitmap, w.getBitmap());
		// Values greater than max should never be allowed even if centre > max
		try {
			w.setSeen(MAX_32_BIT_UNSIGNED + 1);
			fail();
		} catch (IllegalArgumentException expected) {}
	}

	@Test
	public void testWindowLowerLimit() {
		ReorderingWindow w = new ReorderingWindow();
		// Centre is 0, negative values should never be allowed
		try {
			w.setSeen(-1);
			fail();
		} catch (IllegalArgumentException expected) {}
		// Slide the window
		w.setSeen(15);
		// Centre is 16, lowest value in window is 0
		w.setSeen(0);
		// Slide the window
		w.setSeen(16);
		// Centre is 17, lowest value in window is 1
		w.setSeen(1);
		try {
			w.setSeen(0);
			fail();
		} catch (IllegalArgumentException expected) {}
		// Slide the window
		w.setSeen(25);
		// Centre is 26, lowest value in window is 10
		w.setSeen(10);
		try {
			w.setSeen(9);
			fail();
		} catch (IllegalArgumentException expected) {}
		// Centre should still be 26
		assertEquals(26, w.getCentre());
		// The bits corresponding to 10, 15, 16 and 25 should be set
		byte[] expectedBitmap = new byte[REORDERING_WINDOW_SIZE / 8];
		expectedBitmap[0] = (byte) 134; // 10000110
		expectedBitmap[1] = 1; // 00000001
		assertArrayEquals(expectedBitmap, w.getBitmap());
	}

	@Test
	public void testCannotSetSeenTwice() {
		ReorderingWindow w = new ReorderingWindow();
		w.setSeen(15);
		try {
			w.setSeen(15);
			fail();
		} catch (IllegalArgumentException expected) {}
	}

	@Test
	public void testGetUnseenStreamNumbers() {
		ReorderingWindow w = new ReorderingWindow();
		// Centre is 0; window should cover 0 to 15, inclusive, with none seen
		Collection<Long> unseen = w.getUnseen();
		assertEquals(16, unseen.size());
		for (int i = 0; i < 16; i++) {
			assertTrue(unseen.contains(Long.valueOf(i)));
			assertFalse(w.isSeen(i));
		}
		w.setSeen(3);
		w.setSeen(4);
		// Centre is 5; window should cover 0 to 20, inclusive, with two seen
		unseen = w.getUnseen();
		assertEquals(19, unseen.size());
		for (int i = 0; i < 21; i++) {
			if (i == 3 || i == 4) {
				assertFalse(unseen.contains(Long.valueOf(i)));
				assertTrue(w.isSeen(i));
			} else {
				assertTrue(unseen.contains(Long.valueOf(i)));
				assertFalse(w.isSeen(i));
			}
		}
		w.setSeen(19);
		// Centre is 20; window should cover 4 to 35, inclusive, with two seen
		unseen = w.getUnseen();
		assertEquals(30, unseen.size());
		for (int i = 4; i < 36; i++) {
			if (i == 4 || i == 19) {
				assertFalse(unseen.contains(Long.valueOf(i)));
				assertTrue(w.isSeen(i));
			} else {
				assertTrue(unseen.contains(Long.valueOf(i)));
				assertFalse(w.isSeen(i));
			}
		}
	}
}
