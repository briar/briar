package net.sf.briar.transport;

import junit.framework.TestCase;

import org.junit.Test;

public class ConnectionWindowImplTest extends TestCase {

	@Test
	public void testWindowSliding() {
		ConnectionWindowImpl w = new ConnectionWindowImpl(0L, 0);
		for(int i = 0; i < 100; i++) {
			assertFalse(w.isSeen(i));
			w.setSeen(i);
			assertTrue(w.isSeen(i));
		}
	}

	@Test
	public void testWindowJumping() {
		ConnectionWindowImpl w = new ConnectionWindowImpl(0L, 0);
		for(int i = 0; i < 100; i += 13) {
			assertFalse(w.isSeen(i));
			w.setSeen(i);
			assertTrue(w.isSeen(i));
		}
	}

	@Test
	public void testWindowUpperLimit() {
		ConnectionWindowImpl w = new ConnectionWindowImpl(0L, 0);
		// Centre is 0, highest value in window is 15
		w.setSeen(15);
		// Centre is 16, highest value in window is 31
		w.setSeen(31);
		try {
			// Centre is 32, highest value in window is 47
			w.setSeen(48);
			fail();
		} catch(IllegalArgumentException expected) {}
	}

	@Test
	public void testWindowLowerLimit() {
		ConnectionWindowImpl w = new ConnectionWindowImpl(0L, 0);
		// Centre is 0, negative values should never be allowed
		try {
			w.setSeen(-1);
			fail();
		} catch(IllegalArgumentException expected) {}
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
		} catch(IllegalArgumentException expected) {}
		// Slide the window
		w.setSeen(25);
		// Centre is 26, lowest value in window is 10
		w.setSeen(10);
		try {
			w.setSeen(9);
			fail();
		} catch(IllegalArgumentException expected) {}
	}

	@Test
	public void testCannotSetSameValueTwice() {
		ConnectionWindowImpl w = new ConnectionWindowImpl(0L, 0);
		w.setSeen(15);
		try {
			w.setSeen(15);
			fail();
		} catch(IllegalArgumentException expected) {}
	}
}
