package org.briarproject.crypto;

import static org.junit.Assert.assertArrayEquals;

import java.util.Random;

import org.briarproject.BriarTestCase;
import org.briarproject.api.crypto.SecretKey;
import org.junit.Test;

public class SecretKeyImplTest extends BriarTestCase {

	private static final int KEY_BYTES = 32; // 256 bits

	@Test
	public void testCopiesAreErased() {
		byte[] master = new byte[KEY_BYTES];
		new Random().nextBytes(master);
		SecretKey k = new SecretKeyImpl(master);
		byte[] copy = k.getEncoded();
		assertArrayEquals(master, copy);
		k.erase();
		byte[] blank = new byte[KEY_BYTES];
		assertArrayEquals(blank, master);
		assertArrayEquals(blank, copy);
	}
}
