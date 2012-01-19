package net.sf.briar.transport;

import net.sf.briar.BriarTestCase;
import static net.sf.briar.api.transport.TransportConstants.FRAME_WINDOW_SIZE;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import org.junit.Test;

public class FrameWindowImplTest extends BriarTestCase {

	@Test
	public void testWindowSliding() {
		FrameWindow w = new FrameWindowImpl();
		for(int i = 0; i < 100; i++) {
			assertTrue(w.contains(i));
			w.remove(i);
			assertFalse(w.contains(i));
		}
	}

	@Test
	public void testWindowJumping() {
		FrameWindow w = new FrameWindowImpl();
		for(int i = 0; i < 100; i += 13) {
			assertTrue(w.contains(i));
			w.remove(i);
			assertFalse(w.contains(i));
		}
	}

	@Test
	public void testWindowUpperLimit() {
		FrameWindow w = new FrameWindowImpl();
		// Centre is 0, highest value in window is 15
		for(int i = 0; i < 16; i++) assertTrue(w.contains(i));
		assertFalse(w.remove(16));
		assertTrue(w.remove(15));
		assertFalse(w.remove(15));
		// Centre is 16, highest value in window is 31
		for(int i = 0; i < 32; i++) assertEquals(i != 15, w.contains(i));
		assertFalse(w.remove(32));
		assertTrue(w.remove(31));
		// Values greater than 2^32 - 1 should never be allowed
		assertTrue(w.advance(MAX_32_BIT_UNSIGNED - 1));
		assertTrue(w.remove(MAX_32_BIT_UNSIGNED - 1));
		assertTrue(w.remove(MAX_32_BIT_UNSIGNED));
		try {
			w.remove(MAX_32_BIT_UNSIGNED + 1);
			fail();
		} catch(IllegalArgumentException expected) {}
	}

	@Test
	public void testAdvance() {
		FrameWindow w = new FrameWindowImpl();
		long centre = 0;
		for(int i = 0; i < 10; i++) {
			long bottom = Math.max(0, centre - FRAME_WINDOW_SIZE / 2);
			long top = Math.min(MAX_32_BIT_UNSIGNED,
					centre + FRAME_WINDOW_SIZE / 2 - 1);
			if(bottom > 0) assertFalse(w.contains(bottom - 1));
			for(long j = bottom; j <= top; j++) assertTrue(w.contains(j));
			if(top < MAX_32_BIT_UNSIGNED) assertFalse(w.contains(top + 1));
			centre += 12345;
			assertTrue(w.advance(centre));
		}
	}

	@Test
	public void testWindowLowerLimit() {
		FrameWindow w = new FrameWindowImpl();
		// Centre is 0, negative values should never be allowed
		try {
			w.remove(-1);
			fail();
		} catch(IllegalArgumentException expected) {}
		// Slide the window twice
		assertTrue(w.remove(15));
		assertTrue(w.remove(16));
		// Centre is 17, lowest value in window is 1
		assertFalse(w.remove(0));
		assertTrue(w.remove(1));
		// Slide the window
		assertTrue(w.remove(25));
		// Centre is 26, lowest value in window is 10
		assertFalse(w.remove(9));
		assertTrue(w.remove(10));
	}
}