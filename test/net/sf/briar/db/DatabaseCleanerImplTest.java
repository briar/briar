package net.sf.briar.db;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.db.DbException;
import net.sf.briar.db.DatabaseCleaner.Callback;

import org.junit.Test;

public class DatabaseCleanerImplTest extends BriarTestCase {

	@Test
	public void testStoppingCleanerWakesItUp() throws Exception {
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
		DatabaseCleanerImpl cleaner = new DatabaseCleanerImpl();
		long start = System.currentTimeMillis();
		// Start the cleaner and check that shouldCheckFreeSpace() is called
		cleaner.startCleaning(callback, 30L * 1000L);
		assertTrue(latch.await(5, TimeUnit.SECONDS));
		// Stop the cleaner (it should be waiting between sweeps)
		cleaner.stopCleaning();
		long end = System.currentTimeMillis();
		// Check that much less than 30 seconds expired
		assertTrue(end - start < 10L * 1000L);
	}
}
