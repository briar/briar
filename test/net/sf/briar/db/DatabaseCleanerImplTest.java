package net.sf.briar.db;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.db.DbException;
import net.sf.briar.db.DatabaseCleaner.Callback;

import org.junit.Test;

public class DatabaseCleanerImplTest extends BriarTestCase {

	@Test
	public void testCleanerRunsPeriodically() throws Exception {
		final CountDownLatch latch = new CountDownLatch(5);
		Callback callback = new Callback() {

			public void checkFreeSpaceAndClean() throws DbException {
				latch.countDown();
			}

			public boolean shouldCheckFreeSpace() {
				return true;
			}
		};
		DatabaseCleanerImpl cleaner = new DatabaseCleanerImpl();
		// Start the cleaner
		cleaner.startCleaning(callback, 10L);
		// The database should be cleaned five times (allow 5s for system load)
		assertTrue(latch.await(5, TimeUnit.SECONDS));
		// Stop the cleaner
		cleaner.stopCleaning();
	}

	@Test
	public void testStoppingCleanerWakesItUp() throws Exception {
		final CountDownLatch latch = new CountDownLatch(1);
		Callback callback = new Callback() {

			public void checkFreeSpaceAndClean() throws DbException {
				latch.countDown();
			}

			public boolean shouldCheckFreeSpace() {
				return true;
			}
		};
		DatabaseCleanerImpl cleaner = new DatabaseCleanerImpl();
		long start = System.currentTimeMillis();
		// Start the cleaner
		cleaner.startCleaning(callback, 10L * 1000L);
		// The database should be cleaned once at startup
		assertTrue(latch.await(5, TimeUnit.SECONDS));
		// Stop the cleaner (it should be waiting between sweeps)
		cleaner.stopCleaning();
		long end = System.currentTimeMillis();
		// Check that much less than 10 seconds expired
		assertTrue(end - start < 10L * 1000L);
	}
}
