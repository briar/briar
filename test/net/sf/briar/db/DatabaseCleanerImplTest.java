package net.sf.briar.db;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;
import net.sf.briar.api.db.DbException;
import net.sf.briar.db.DatabaseCleaner.Callback;

import org.junit.Test;

public class DatabaseCleanerImplTest extends TestCase {

	@Test
	public void testStoppingCleanerWakesItUp() throws DbException {
		final CountDownLatch latch = new CountDownLatch(1);
		Callback callback = new Callback() {

			public void checkFreeSpaceAndClean() throws DbException {
				throw new IllegalStateException();
			}

			public boolean shouldCheckFreeSpace() {
				latch.countDown();
				return false;
			}
		};
		// Configure the cleaner to wait for 30 seconds between sweeps
		DatabaseCleanerImpl cleaner =
			new DatabaseCleanerImpl(callback, 30 * 1000);
		long start = System.currentTimeMillis();
		// Start the cleaner and check that shouldCheckFreeSpace() is called
		cleaner.startCleaning();
		try {
			assertTrue(latch.await(5, TimeUnit.SECONDS));
		} catch(InterruptedException e) {
			fail();
		}
		// Stop the cleaner (it should be waiting between sweeps)
		cleaner.stopCleaning();
		long end = System.currentTimeMillis();
		// Check that much less than 30 seconds expired
		assertTrue(end - start < 10 * 1000);
	}
}
