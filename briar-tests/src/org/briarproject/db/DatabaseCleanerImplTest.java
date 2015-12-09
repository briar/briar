package org.briarproject.db;

import org.briarproject.BriarTestCase;
import org.briarproject.api.db.DbException;
import org.briarproject.api.system.Timer;
import org.briarproject.db.DatabaseCleaner.Callback;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class DatabaseCleanerImplTest extends BriarTestCase {

	@Test
	public void testCleanerRunsPeriodically() throws Exception {
		final AtomicInteger cleans = new AtomicInteger(0);
		Callback callback = new Callback() {

			boolean check = true;

			public void checkFreeSpaceAndClean() throws DbException {
				cleans.incrementAndGet();
			}

			public boolean shouldCheckFreeSpace() {
				// Alternate between true and false
				check = !check;
				return !check;
			}
		};
		Mockery context = new Mockery();
		final Timer timer = context.mock(Timer.class);
		final DatabaseCleanerImpl cleaner = new DatabaseCleanerImpl(timer);
		context.checking(new Expectations() {{
			oneOf(timer).scheduleAtFixedRate(cleaner, 0, 10);
			oneOf(timer).cancel();
		}});
		// Start the cleaner - it should schedule itself with the timer
		cleaner.startCleaning(callback, 10);
		// Call the cleaner's run method six times
		for (int i = 0; i < 6; i++) cleaner.run();
		// Stop the cleaner - it should cancel the timer
		cleaner.stopCleaning();
		// The database should have been cleaned three times
		assertEquals(3, cleans.get());
		context.assertIsSatisfied();
	}
}
