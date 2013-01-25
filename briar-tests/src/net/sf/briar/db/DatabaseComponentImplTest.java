package net.sf.briar.db;

import static net.sf.briar.db.DatabaseConstants.BYTES_PER_SWEEP;
import static net.sf.briar.db.DatabaseConstants.MIN_FREE_SPACE;

import java.util.Collections;

import net.sf.briar.api.clock.SystemClock;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.db.DatabaseCleaner.Callback;

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
		context.checking(new Expectations() {{
			oneOf(database).getFreeSpace();
			will(returnValue(MIN_FREE_SPACE));
		}});
		Callback db = createDatabaseComponentImpl(database, cleaner, shutdown);

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
		Callback db = createDatabaseComponentImpl(database, cleaner, shutdown);

		db.checkFreeSpaceAndClean();

		context.assertIsSatisfied();
	}

	@Test
	public void testExpiringUnsendableMessageDoesNotTriggerBackwardInclusion()
			throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).getFreeSpace();
			will(returnValue(MIN_FREE_SPACE - 1));
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getOldMessages(txn, BYTES_PER_SWEEP);
			will(returnValue(Collections.singletonList(messageId)));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).removeMessage(txn, messageId);
			oneOf(database).commitTransaction(txn);
			oneOf(database).getFreeSpace();
			will(returnValue(MIN_FREE_SPACE));
		}});
		Callback db = createDatabaseComponentImpl(database, cleaner, shutdown);

		db.checkFreeSpaceAndClean();

		context.assertIsSatisfied();
	}

	@Test
	public void testExpiringSendableMessageTriggersBackwardInclusion()
			throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).getFreeSpace();
			will(returnValue(MIN_FREE_SPACE - 1));
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getOldMessages(txn, BYTES_PER_SWEEP);
			will(returnValue(Collections.singletonList(messageId)));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(1));
			oneOf(database).getGroupMessageParent(txn, messageId);
			will(returnValue(null));
			oneOf(database).removeMessage(txn, messageId);
			oneOf(database).commitTransaction(txn);
			oneOf(database).getFreeSpace();
			will(returnValue(MIN_FREE_SPACE));
		}});
		Callback db = createDatabaseComponentImpl(database, cleaner, shutdown);

		db.checkFreeSpaceAndClean();

		context.assertIsSatisfied();
	}

	@Override
	protected <T> DatabaseComponent createDatabaseComponent(
			Database<T> database, DatabaseCleaner cleaner,
			ShutdownManager shutdown) {
		return createDatabaseComponentImpl(database, cleaner, shutdown);
	}

	private <T> DatabaseComponentImpl<T> createDatabaseComponentImpl(
			Database<T> database, DatabaseCleaner cleaner,
			ShutdownManager shutdown) {
		return new DatabaseComponentImpl<T>(database, cleaner, shutdown,
				new SystemClock());
	}
}
