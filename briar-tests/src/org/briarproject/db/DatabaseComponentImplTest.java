package org.briarproject.db;

import static org.briarproject.db.DatabaseConstants.BYTES_PER_SWEEP;
import static org.briarproject.db.DatabaseConstants.MIN_FREE_SPACE;

import java.util.Collections;

import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.db.DatabaseCleaner.Callback;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

/**
 * Tests that use the DatabaseCleaner.Callback interface of
 * DatabaseComponentImpl.
 */
public class DatabaseComponentImplTest extends DatabaseComponentTest {

	@Test
	public void testNotCleanedIfEnoughFreeSpace() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			oneOf(database).getFreeSpace();
			will(returnValue(MIN_FREE_SPACE));
		}});
		Callback db = createDatabaseComponentImpl(database, cleaner, eventBus,
				shutdown);

		db.checkFreeSpaceAndClean();

		context.assertIsSatisfied();
	}

	@Test
	public void testCleanedIfNotEnoughFreeSpace() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			oneOf(database).getFreeSpace();
			will(returnValue(MIN_FREE_SPACE - 1));
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getOldMessages(txn, BYTES_PER_SWEEP);
			will(returnValue(Collections.emptyList()));
			oneOf(database).commitTransaction(txn);
			// As if by magic, some free space has appeared
			oneOf(database).getFreeSpace();
			will(returnValue(MIN_FREE_SPACE));
		}});
		Callback db = createDatabaseComponentImpl(database, cleaner, eventBus,
				shutdown);

		db.checkFreeSpaceAndClean();

		context.assertIsSatisfied();
	}

	@Override
	protected <T> DatabaseComponent createDatabaseComponent(
			Database<T> database, DatabaseCleaner cleaner, EventBus eventBus,
			ShutdownManager shutdown) {
		return createDatabaseComponentImpl(database, cleaner, eventBus,
				shutdown);
	}

	private <T> DatabaseComponentImpl<T> createDatabaseComponentImpl(
			Database<T> database, DatabaseCleaner cleaner, EventBus eventBus,
			ShutdownManager shutdown) {
		return new DatabaseComponentImpl<T>(database, cleaner, eventBus,
				shutdown);
	}
}
