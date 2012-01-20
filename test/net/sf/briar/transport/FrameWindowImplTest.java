package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_WINDOW_SIZE;
import net.sf.briar.BriarTestCase;

import org.junit.Test;

public class FrameWindowImplTest extends BriarTestCase {

	@Test
	public void testWindowSliding() {
		FrameWindow w = new FrameWindowImpl();
		for(int i = 0; i < 100; i++) {
			assertTrue(w.contains(i));
			assertTrue(w.remove(i));
			assertFalse(w.contains(i));
		}
		for(int i = 100; i < 100 + FRAME_WINDOW_SIZE; i++) {
			assertTrue(w.contains(i));
			assertFalse(w.isTooHigh(i));
		}
		assertFalse(w.contains(100 + FRAME_WINDOW_SIZE));
		assertTrue(w.isTooHigh(100 + FRAME_WINDOW_SIZE));
	}

	@Test
	public void testWindowJumping() {
		FrameWindow w = new FrameWindowImpl();
		// Base of the window is 0
		for(int i = 0; i < FRAME_WINDOW_SIZE; i++) assertTrue(w.contains(i));
		assertFalse(w.contains(FRAME_WINDOW_SIZE));
		assertFalse(w.isTooHigh(FRAME_WINDOW_SIZE - 1));
		assertTrue(w.isTooHigh(FRAME_WINDOW_SIZE));
		// Remove all numbers except 0 and 5
		for(int i = 1; i < 5; i++) assertTrue(w.remove(i));
		for(int i = 6; i < FRAME_WINDOW_SIZE; i++) assertTrue(w.remove(i));
		// Base of the window should still be 0
		assertTrue(w.contains(0));
		for(int i = 1; i < 5; i++) assertFalse(w.contains(i));
		assertTrue(w.contains(5));
		for(int i = 6; i < FRAME_WINDOW_SIZE; i++) assertFalse(w.contains(i));
		assertFalse(w.contains(FRAME_WINDOW_SIZE));
		assertFalse(w.isTooHigh(FRAME_WINDOW_SIZE - 1));
		assertTrue(w.isTooHigh(FRAME_WINDOW_SIZE));
		// Remove 0
		assertTrue(w.remove(0));
		// Base of the window should now be 5
		for(int i = 0; i < 5; i++) assertFalse(w.contains(i));
		assertTrue(w.contains(5));
		for(int i = 6; i < FRAME_WINDOW_SIZE; i++) assertFalse(w.contains(i));
		for(int i = FRAME_WINDOW_SIZE; i < FRAME_WINDOW_SIZE + 5; i++) {
			assertTrue(w.contains(i));
		}
		assertFalse(w.contains(FRAME_WINDOW_SIZE + 5));
		assertFalse(w.isTooHigh(FRAME_WINDOW_SIZE + 4));
		assertTrue(w.isTooHigh(FRAME_WINDOW_SIZE + 5));
		// Remove all numbers except 5
		for(int i = FRAME_WINDOW_SIZE; i < FRAME_WINDOW_SIZE + 5; i++) {
			assertTrue(w.remove(i));
		}
		// Base of the window should still be 5
		assertTrue(w.contains(5));
		for(int i = 6; i < FRAME_WINDOW_SIZE + 5; i++) {
			assertFalse(w.contains(i));
		}
		assertFalse(w.contains(FRAME_WINDOW_SIZE + 5));
		assertFalse(w.isTooHigh(FRAME_WINDOW_SIZE + 4));
		assertTrue(w.isTooHigh(FRAME_WINDOW_SIZE + 5));
		// Remove 5
		assertTrue(w.remove(5));
		// Base of the window should now be FRAME_WINDOW_SIZE + 5
		for(int i = 0; i < FRAME_WINDOW_SIZE + 5; i++) {
			assertFalse(w.contains(i));
		}
		for(int i = FRAME_WINDOW_SIZE + 5; i < FRAME_WINDOW_SIZE * 2 + 5; i++) {
			assertTrue(w.contains(i));
		}
		assertFalse(w.contains(FRAME_WINDOW_SIZE * 2 + 5));
		assertFalse(w.isTooHigh(FRAME_WINDOW_SIZE * 2 + 4));
		assertTrue(w.isTooHigh(FRAME_WINDOW_SIZE * 2 + 5));
	}
}