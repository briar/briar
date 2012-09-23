package net.sf.briar.crypto;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.db.DbException;
import net.sf.briar.crypto.KeyRotatorImpl;
import net.sf.briar.crypto.KeyRotator.Callback;

import org.junit.Test;

public class KeyRotatorImplTest extends BriarTestCase {

	@Test
	public void testCleanerRunsPeriodically() throws Exception {
		final CountDownLatch latch = new CountDownLatch(5);
		Callback callback = new Callback() {

			public void rotateKeys() throws DbException {
				latch.countDown();
			}
		};
		KeyRotatorImpl cleaner = new KeyRotatorImpl();
		// Start the rotator
		cleaner.startRotating(callback, 10L);
		// The keys should be rotated five times (allow 5 secs for system load)
		assertTrue(latch.await(5, TimeUnit.SECONDS));
		// Stop the rotator
		cleaner.stopRotating();
	}

	@Test
	public void testStoppingCleanerWakesItUp() throws Exception {
		final CountDownLatch latch = new CountDownLatch(1);
		Callback callback = new Callback() {

			public void rotateKeys() throws DbException {
				latch.countDown();
			}
		};
		KeyRotatorImpl cleaner = new KeyRotatorImpl();
		long start = System.currentTimeMillis();
		// Start the rotator
		cleaner.startRotating(callback, 10L * 1000L);
		// The keys should be rotated once at startup
		assertTrue(latch.await(5, TimeUnit.SECONDS));
		// Stop the rotator (it should be waiting between rotations)
		cleaner.stopRotating();
		long end = System.currentTimeMillis();
		// Check that much less than 10 seconds expired
		assertTrue(end - start < 10L * 1000L);
	}
}
