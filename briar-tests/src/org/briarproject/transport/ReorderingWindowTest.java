package org.briarproject.transport;

import org.briarproject.BriarTestCase;
import org.briarproject.api.transport.TransportConstants;
import org.briarproject.transport.ReorderingWindow.Change;
import org.junit.Assert;
import org.junit.Test;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import static org.briarproject.api.transport.TransportConstants.REORDERING_WINDOW_SIZE;
import static org.junit.Assert.assertArrayEquals;

public class ReorderingWindowTest extends BriarTestCase {

	@Test
	public void testBitmapConversion() {
		Random random = new Random();
		byte[] bitmap = new byte[REORDERING_WINDOW_SIZE / 8];
		for (int i = 0; i < 1000; i++) {
			random.nextBytes(bitmap);
			ReorderingWindow window = new ReorderingWindow(0L, bitmap);
			assertArrayEquals(bitmap, window.getBitmap());
		}
	}

	@Test
	public void testWindowSlidesWhenFirstElementIsSeen() {
		byte[] bitmap = new byte[REORDERING_WINDOW_SIZE / 8];
		ReorderingWindow window = new ReorderingWindow(0L, bitmap);
		// Set the first element seen
		Change change = window.setSeen(0L);
		// The window should slide by one element
		assertEquals(1L, window.getBase());
		assertEquals(Collections.singletonList((long) REORDERING_WINDOW_SIZE), change.getAdded());
		assertEquals(Collections.singletonList(0L), change.getRemoved());
		// All elements in the window should be unseen
		assertArrayEquals(bitmap, window.getBitmap());
	}

	@Test
	public void testWindowDoesNotSlideWhenElementBelowMidpointIsSeen() {
		byte[] bitmap = new byte[REORDERING_WINDOW_SIZE / 8];
		ReorderingWindow window = new ReorderingWindow(0L, bitmap);
		// Set an element below the midpoint seen
		Change change = window.setSeen(1L);
		// The window should not slide
		assertEquals(0L, window.getBase());
		assertEquals(Collections.emptyList(), change.getAdded());
		assertEquals(Collections.singletonList(1L), change.getRemoved());
		// The second element in the window should be seen
		bitmap[0] = 0x40; // 0100 0000
		assertArrayEquals(bitmap, window.getBitmap());
	}

	@Test
	public void testWindowSlidesWhenElementAboveMidpointIsSeen() {
		byte[] bitmap = new byte[REORDERING_WINDOW_SIZE / 8];
		ReorderingWindow window = new ReorderingWindow(0, bitmap);
		long aboveMidpoint = REORDERING_WINDOW_SIZE / 2;
		// Set an element above the midpoint seen
		Change change = window.setSeen(aboveMidpoint);
		// The window should slide by one element
		assertEquals(1L, window.getBase());
		assertEquals(Collections.singletonList((long) REORDERING_WINDOW_SIZE), change.getAdded());
		assertEquals(Arrays.asList(0L, aboveMidpoint), change.getRemoved());
		// The highest element below the midpoint should be seen
		bitmap[bitmap.length / 2 - 1] = (byte) 0x01; // 0000 0001
		assertArrayEquals(bitmap, window.getBitmap());
	}

	@Test
	public void testWindowSlidesUntilLowestElementIsUnseenWhenFirstElementIsSeen() {
		byte[] bitmap = new byte[REORDERING_WINDOW_SIZE / 8];
		ReorderingWindow window = new ReorderingWindow(0L, bitmap);
		window.setSeen(1L);
		// Set the first element seen
		Change change = window.setSeen(0L);
		// The window should slide by two elements
		assertEquals(2L, window.getBase());
		assertEquals(Arrays.asList((long) REORDERING_WINDOW_SIZE,
				(long) (REORDERING_WINDOW_SIZE + 1)), change.getAdded());
		assertEquals(Collections.singletonList(0L), change.getRemoved());
		// All elements in the window should be unseen
		assertArrayEquals(bitmap, window.getBitmap());
	}

	@Test
	public void testWindowSlidesUntilLowestElementIsUnseenWhenElementAboveMidpointIsSeen() {
		byte[] bitmap = new byte[REORDERING_WINDOW_SIZE / 8];
		ReorderingWindow window = new ReorderingWindow(0L, bitmap);
		window.setSeen(1L);
		long aboveMidpoint = REORDERING_WINDOW_SIZE / 2;
		// Set an element above the midpoint seen
		Change change = window.setSeen(aboveMidpoint);
		// The window should slide by two elements
		assertEquals(2L, window.getBase());
		assertEquals(Arrays.asList((long) REORDERING_WINDOW_SIZE,
				(long) (REORDERING_WINDOW_SIZE + 1)), change.getAdded());
		assertEquals(Arrays.asList(0L, aboveMidpoint), change.getRemoved());
		// The second-highest element below the midpoint should be seen
		bitmap[bitmap.length / 2 - 1] = (byte) 0x02; // 0000 0010
		assertArrayEquals(bitmap, window.getBitmap());
	}
}
