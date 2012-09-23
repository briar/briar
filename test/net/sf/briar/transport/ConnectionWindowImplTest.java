package net.sf.briar.transport;

import java.util.HashSet;
import java.util.Set;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.transport.ConnectionWindow;
import net.sf.briar.util.ByteUtils;

import org.junit.Test;

public class ConnectionWindowImplTest extends BriarTestCase {

	@Test
	public void testWindowSliding() {
		ConnectionWindow w = new ConnectionWindowImpl();
		for(int i = 0; i < 100; i++) {
			assertFalse(w.isSeen(i));
			w.setSeen(i);
			assertTrue(w.isSeen(i));
		}
	}

	@Test
	public void testWindowJumping() {
		ConnectionWindow w = new ConnectionWindowImpl();
		for(int i = 0; i < 100; i += 13) {
			assertFalse(w.isSeen(i));
			w.setSeen(i);
			assertTrue(w.isSeen(i));
		}
	}

	@Test
	public void testWindowUpperLimit() {
		ConnectionWindow w = new ConnectionWindowImpl();
		// Centre is 0, highest value in window is 15
		w.setSeen(15);
		// Centre is 16, highest value in window is 31
		w.setSeen(31);
		try {
			// Centre is 32, highest value in window is 47
			w.setSeen(48);
			fail();
		} catch(IllegalArgumentException expected) {}
		// Values greater than 2^32 - 1 should never be allowed
		Set<Long> unseen = new HashSet<Long>();
		for(int i = 0; i < 32; i++) {
			unseen.add(ByteUtils.MAX_32_BIT_UNSIGNED - i);
		}
		w = new ConnectionWindowImpl(unseen);
		w.setSeen(ByteUtils.MAX_32_BIT_UNSIGNED);
		try {
			w.setSeen(ByteUtils.MAX_32_BIT_UNSIGNED + 1);
			fail();
		} catch(IllegalArgumentException expected) {}
	}

	@Test
	public void testWindowLowerLimit() {
		ConnectionWindow w = new ConnectionWindowImpl();
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
	public void testCannotSetSeenTwice() {
		ConnectionWindow w = new ConnectionWindowImpl();
		w.setSeen(15);
		try {
			w.setSeen(15);
			fail();
		} catch(IllegalArgumentException expected) {}
	}

	@Test
	public void testGetUnseenConnectionNumbers() {
		ConnectionWindow w = new ConnectionWindowImpl();
		// Centre is 0; window should cover 0 to 15, inclusive, with none seen
		Set<Long> unseen = w.getUnseen();
		assertEquals(16, unseen.size());
		for(int i = 0; i < 16; i++) {
			assertTrue(unseen.contains(Long.valueOf(i)));
			assertFalse(w.isSeen(i));
		}
		w.setSeen(3);
		w.setSeen(4);
		// Centre is 5; window should cover 0 to 20, inclusive, with two seen
		unseen = w.getUnseen();
		assertEquals(19, unseen.size());
		for(int i = 0; i < 21; i++) {
			if(i == 3 || i == 4) {
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
		for(int i = 4; i < 36; i++) {
			if(i == 4 || i == 19) {
				assertFalse(unseen.contains(Long.valueOf(i)));
				assertTrue(w.isSeen(i));
			} else {
				assertTrue(unseen.contains(Long.valueOf(i)));
				assertFalse(w.isSeen(i));
			}
		}
	}
}
