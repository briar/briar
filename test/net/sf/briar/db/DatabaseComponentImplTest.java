package net.sf.briar.db;

import static net.sf.briar.api.db.DatabaseComponent.BYTES_PER_SWEEP;
import static net.sf.briar.api.db.DatabaseComponent.MIN_FREE_SPACE;

import java.util.Collections;

import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.BatchBuilder;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.db.DatabaseCleaner.Callback;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import com.google.inject.Provider;

/**
 * Tests that use the DatabaseCleaner.Callback interface of
 * DatabaseComponentImpl.
 */
public abstract class DatabaseComponentImplTest extends DatabaseComponentTest {

	protected abstract <T> DatabaseComponentImpl<T> createDatabaseComponentImpl(
			Database<T> database, DatabaseCleaner cleaner,
			Provider<BatchBuilder> batchBuilderProvider);

	@Test
	public void testNotCleanedIfEnoughFreeSpace() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<BatchBuilder> batchBuilderProvider =
			context.mock(Provider.class);
		context.checking(new Expectations() {{
			oneOf(database).getFreeSpace();
			will(returnValue(MIN_FREE_SPACE));
		}});
		Callback db = createDatabaseComponentImpl(database, cleaner,
				batchBuilderProvider);

		db.checkFreeSpaceAndClean();

		context.assertIsSatisfied();
	}

	@Test
	public void testCleanedIfNotEnoughFreeSpace() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<BatchBuilder> batchBuilderProvider =
			context.mock(Provider.class);
		context.checking(new Expectations() {{
			oneOf(database).getFreeSpace();
			will(returnValue(MIN_FREE_SPACE - 1));
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getOldMessages(txn, BYTES_PER_SWEEP);
			will(returnValue(Collections.emptySet()));
			oneOf(database).commitTransaction(txn);
			// As if by magic, some free space has appeared
			oneOf(database).getFreeSpace();
			will(returnValue(MIN_FREE_SPACE));
		}});
		Callback db = createDatabaseComponentImpl(database, cleaner,
				batchBuilderProvider);

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
		@SuppressWarnings("unchecked")
		final Provider<BatchBuilder> batchBuilderProvider =
			context.mock(Provider.class);
		context.checking(new Expectations() {{
			oneOf(database).getFreeSpace();
			will(returnValue(MIN_FREE_SPACE - 1));
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getOldMessages(txn, BYTES_PER_SWEEP);
			will(returnValue(Collections.singleton(messageId)));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).removeMessage(txn, messageId);
			oneOf(database).commitTransaction(txn);
			oneOf(database).getFreeSpace();
			will(returnValue(MIN_FREE_SPACE));
		}});
		Callback db = createDatabaseComponentImpl(database, cleaner,
				batchBuilderProvider);

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
		@SuppressWarnings("unchecked")
		final Provider<BatchBuilder> batchBuilderProvider =
			context.mock(Provider.class);
		context.checking(new Expectations() {{
			oneOf(database).getFreeSpace();
			will(returnValue(MIN_FREE_SPACE - 1));
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getOldMessages(txn, BYTES_PER_SWEEP);
			will(returnValue(Collections.singleton(messageId)));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(1));
			oneOf(database).getParent(txn, messageId);
			will(returnValue(MessageId.NONE));
			oneOf(database).removeMessage(txn, messageId);
			oneOf(database).commitTransaction(txn);
			oneOf(database).getFreeSpace();
			will(returnValue(MIN_FREE_SPACE));
		}});
		Callback db = createDatabaseComponentImpl(database, cleaner,
				batchBuilderProvider);

		db.checkFreeSpaceAndClean();

		context.assertIsSatisfied();
	}
}
