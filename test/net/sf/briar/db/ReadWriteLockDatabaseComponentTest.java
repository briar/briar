package net.sf.briar.db;

import net.sf.briar.api.db.DatabaseComponent;

public class ReadWriteLockDatabaseComponentTest
extends DatabaseComponentImplTest {

	@Override
	protected <T> DatabaseComponent createDatabaseComponent(
			Database<T> database, DatabaseCleaner cleaner) {
		return createDatabaseComponentImpl(database, cleaner);
	}

	@Override
	protected <T> DatabaseComponentImpl<T> createDatabaseComponentImpl(
			Database<T> database, DatabaseCleaner cleaner) {
		return new ReadWriteLockDatabaseComponent<T>(database, cleaner);
	}
}
